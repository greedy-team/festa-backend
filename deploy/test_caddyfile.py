"""deploy/Caddyfile을 실물 Caddy로 검증한다.

python -m unittest discover -s deploy -p 'test_*.py'

test_deployment.py는 CD의 원격 Bash를 도커 스텁으로 검증한다. 여기는 Caddyfile 자체가
Caddy에서 실제로 뜨는지와, 막기로 한 경로가 정말 막히는지를 본다.

실물로 도는 이유: Caddyfile 문법 오류는 곧 서비스 중단이다. CD는 Caddyfile을 바꾼 뒤
`up -d caddy`로 컨테이너를 **재생성**하므로, `caddy reload`가 가진 "새 설정이 틀리면
기존 설정을 그대로 유지한다"는 안전망이 그 경로에는 없다.

요청까지 컨테이너 안에서 끝낸다. 포트를 호스트에 발행하지 않아 충돌이 없고,
`caddy start`가 뜰 때까지 기다려 주므로 준비 폴링도 없다.
도커가 없는 환경(개발자 윈도우 등)에서는 건너뛴다. CI(우분투 러너)에서 돈다.
"""
import shutil
import subprocess
import unittest
from pathlib import Path

CADDYFILE = Path(__file__).resolve().parents[1] / 'deploy/Caddyfile'
IMAGE = 'caddy:2-alpine'
PATHS = ['/actuator/prometheus', '/actuator/prometheus/', '/actuator/prometheus/anything',
         '/actuator/health', '/api/festivals']

# 서버 상태를 흉내 낸다. 이 색은 실제로 뜨지 않는데, 막히지 않은 경로가 프록시까지
# 갔다는 것을 502로 확인하기 때문에 오히려 그래야 한다.
SETUP = """
set -e
mkdir -p /etc/caddy/upstream
echo 'reverse_proxy app-blue:8080' > /etc/caddy/upstream/active.caddy
"""

# 배포 후 사람이 돌리는 확인 명령과 같은 형태로 상태 코드만 읽는다.
PROBE = """
apk add --no-cache curl > /dev/null
caddy start --config /etc/caddy/Caddyfile > /dev/null
for path in %s; do
  printf '%%s %%s\\n' "$path" \\
    "$(curl -s -o /dev/null -w '%%{http_code}' "http://localhost:8080$path")"
done
""" % ' '.join(PATHS)


def docker_missing():
    if shutil.which('docker') is None:
        return 'docker 없음'
    if subprocess.run(['docker', 'info'], capture_output=True).returncode != 0:
        return 'docker 데몬 없음'
    return None


SKIP = docker_missing()


@unittest.skipIf(SKIP, SKIP or '')
class CaddyfileTest(unittest.TestCase):
    def caddy(self, script):
        return subprocess.run(
            ['docker', 'run', '--rm',
             '-v', f'{CADDYFILE.as_posix()}:/etc/caddy/Caddyfile:ro',
             # 호스트 없이 포트만 주면 자동 HTTPS가 꺼지고, 어떤 Host로 와도 매칭된다.
             # 호스트가 붙으면 매칭에 실패한 요청에 Caddy가 빈 200을 돌려주어
             # 경로 규칙을 보기도 전에 단언이 갈린다.
             '-e', 'API_DOMAINS=:8080',
             '--entrypoint', 'sh', IMAGE, '-c', SETUP + script],
            capture_output=True, text=True, errors='replace', timeout=180)

    def test_caddyfile_is_valid(self):
        """CD가 caddy를 재생성해도 뜨는 설정인가."""
        result = self.caddy('caddy validate --config /etc/caddy/Caddyfile')
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_only_the_observability_endpoint_is_blocked(self):
        """관측 엔드포인트만 404이고 나머지는 앞단을 통과하는가.

        health가 502인 것이 통과의 증거다 — 잘못 막았다면 404가 된다. 경로 규칙이
        아예 안 걸리면 다섯 개가 모두 502이므로, 갈린다는 것 자체가 차단의 증거다.
        """
        result = self.caddy(PROBE)
        self.assertEqual(result.returncode, 0, result.stderr)
        codes = dict(line.split() for line in result.stdout.split('\n')
                     if line.startswith('/') and len(line.split()) == 2)
        self.assertEqual(codes, {'/actuator/prometheus': '404',
                                 '/actuator/prometheus/': '404',
                                 '/actuator/prometheus/anything': '404',
                                 '/actuator/health': '502',
                                 '/api/festivals': '502'})


if __name__ == '__main__':
    unittest.main()
