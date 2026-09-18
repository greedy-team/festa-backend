"""deploy/Caddyfile을 실물 Caddy로 검증한다.

python -m unittest discover -s deploy -p 'test_*.py'

test_deployment.py는 CD의 원격 Bash를 도커 스텁으로 검증한다. 여기는 반대로
Caddyfile 자체가 Caddy에서 실제로 뜨는지와, 막기로 한 경로가 정말 막히는지를 본다.

실물로 도는 이유: Caddyfile 문법 오류는 곧 서비스 중단이다. CD는 Caddyfile을 바꾼 뒤
`up -d caddy`로 컨테이너를 **재생성**하므로, `caddy reload`가 가진 "새 설정이 틀리면
기존 설정을 그대로 유지한다"는 안전망이 그 경로에는 없다.

도커가 없는 환경(개발자 윈도우 등)에서는 통째로 건너뛴다. CI(우분투 러너)에서 돈다.
"""
import shutil
import subprocess
import tempfile
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CADDYFILE = ROOT / 'deploy/Caddyfile'
IMAGE = 'caddy:2-alpine'
CONTAINER = 'festa-caddyfile-test'
SITE_PORT = 8080
HOST_PORT = 8099
# 사이트 주소를 호스트 없이 포트만으로 준다. 이유가 둘이다.
#   1. 호스트 이름이 없으면 자동 HTTPS가 꺼진다 — 테스트에서 인증서를 받을 이유가 없다.
#   2. 어떤 Host 헤더로 와도 이 사이트에 매칭된다. `http://localhost:8080`으로 두면
#      127.0.0.1로 들어온 요청이 어느 사이트에도 안 걸리고, 그때 Caddy는 빈 200을
#      돌려주므로 경로 규칙을 보기도 전에 테스트가 무의미해진다.
# 운영은 실제 도메인이지만 경로 매칭은 호스트와 무관하므로 검증 내용은 같다.
SITE_ADDRESS = f':{SITE_PORT}'


def docker_missing():
    if shutil.which('docker') is None:
        return 'docker 없음'
    if subprocess.run(['docker', 'info'], capture_output=True).returncode != 0:
        return 'docker 데몬 없음'
    return None


SKIP_REASON = docker_missing()


@unittest.skipIf(SKIP_REASON, SKIP_REASON or '')
class CaddyfileTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        upstream = Path(self.temp.name) / 'upstream'
        upstream.mkdir()
        # 서버 상태를 그대로 흉내 낸다. 이 색은 실제로 뜨지 않는데, 막히지 않은 경로가
        # 프록시까지 갔다는 것을 502로 확인하기 때문에 오히려 그래야 한다.
        (upstream / 'active.caddy').write_text('reverse_proxy app-blue:8080\n', encoding='utf-8')
        self.upstream = upstream

    def docker_run(self, *args):
        return subprocess.run(
            ['docker', 'run', '--rm',
             '-v', f'{CADDYFILE.as_posix()}:/etc/caddy/Caddyfile:ro',
             '-v', f'{self.upstream.as_posix()}:/etc/caddy/upstream:ro',
             '-e', f'API_DOMAINS={SITE_ADDRESS}',
             *args],
            capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=180)

    def test_caddyfile_is_valid(self):
        """CD가 caddy를 재생성해도 뜨는 설정인가."""
        result = self.docker_run(IMAGE, 'caddy', 'validate', '--config', '/etc/caddy/Caddyfile')
        self.assertEqual(result.returncode, 0, result.stderr or result.stdout)

    def test_prometheus_is_404_and_health_still_proxies(self):
        """관측 엔드포인트만 막고 나머지는 그대로 앞단을 통과하는가.

        health가 502인 것이 통과의 증거다 — 막혔다면 404가 나온다.
        """
        subprocess.run(['docker', 'rm', '-f', CONTAINER], capture_output=True)
        self.addCleanup(subprocess.run, ['docker', 'rm', '-f', CONTAINER], capture_output=True)

        started = self.docker_run('-d', '--name', CONTAINER,
                                  '-p', f'127.0.0.1:{HOST_PORT}:{SITE_PORT}', IMAGE)
        self.assertEqual(started.returncode, 0, started.stderr)
        self.wait_until_serving()

        self.assertEqual(self.status('/actuator/prometheus'), 404)
        self.assertEqual(self.status('/actuator/prometheus/'), 404)
        # 하위 경로도 같은 규칙에 걸린다.
        self.assertEqual(self.status('/actuator/prometheus/anything'), 404)
        # 합성 체크가 보는 경로. 막히지 않았으므로 프록시로 가서 백엔드 부재로 502다.
        self.assertEqual(self.status('/actuator/health'), 502)
        # 평범한 API 경로도 그대로 간다.
        self.assertEqual(self.status('/api/festivals'), 502)

    def wait_until_serving(self, attempts=60):
        """프록시까지 닿는 상태가 될 때까지 기다린다.

        아무 응답이나 준비 신호로 보면 안 된다. 사이트 매칭에 실패하면 Caddy는 빈 200을
        돌려주는데, 그 상태로 진행하면 경로 규칙을 보지도 않은 채 단언이 갈린다.
        백엔드가 없으므로 502가 "설정이 실려서 프록시까지 갔다"는 신호다.
        """
        last = None
        for _ in range(attempts):
            try:
                last = self.status('/actuator/health')
                if last == 502:
                    return
            except (urllib.error.URLError, OSError):
                pass
            time.sleep(0.5)
        logs = subprocess.run(['docker', 'logs', CONTAINER],
                              capture_output=True, text=True, errors='replace')
        self.fail(f'Caddy가 프록시까지 닿는 상태가 되지 않았다 (마지막 응답 {last})\n'
                  f'{logs.stdout}\n{logs.stderr}')

    def status(self, path):
        url = f'http://127.0.0.1:{HOST_PORT}{path}'
        try:
            with urllib.request.urlopen(url, timeout=5) as response:
                return response.status
        except urllib.error.HTTPError as error:
            return error.code


if __name__ == '__main__':
    unittest.main()
