"""실제 워크플로우의 원격 Bash를 Docker/HTTP 대역으로 실행한다.

python -m unittest discover -s deploy -p 'test_*.py'
실제 컨테이너 기동과 GHCR 인증은 별도 검증한다.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = (ROOT / '.github/workflows/PROJECT-SPRING-CD.yaml').read_text(encoding='utf-8')
REMOTE = re.findall(r"<<'REMOTE'\n(.*?)^          REMOTE", WORKFLOW, re.M | re.S)


def step_script(name):
    """러너에서 도는 스텝의 run 블록을 꺼낸다. 원격 heredoc이 아닌 스텝을 그대로 실행하려고 쓴다."""
    step = WORKFLOW.split(f'      - name: {name}\n')[1]
    step = step.split('      - name:')[0].split('        run: |\n')[1]
    return textwrap.dedent(step)


BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash')
MOCKS = r'''
docker() {
  printf '%s|%s\n' "${APP_IMAGE:-}" "$*" >> "$OCI_DEPLOY_PATH/calls"
  if [[ "$1" == '--config' ]]; then
    printf '%s' "$2" > "$OCI_DEPLOY_PATH/config-path"
    if [[ "$3" == login ]]; then
      cat >/dev/null
      [[ "$SCENARIO" != login_failure ]]
    else
      [[ "$SCENARIO" != pull_failure ]]
    fi
  elif [[ "$*" == 'image inspect '* ]]; then
    [[ "$SCENARIO" != missing_previous || "$3" != "$PREVIOUS" ]]
  elif [[ "$*" == *'ps -q app' ]]; then
    [[ "$SCENARIO" == first_deploy ]] || echo running-container
  elif [[ "$1" == inspect ]]; then
    echo "$PREVIOUS"
  elif [[ "$*" == *'up -d'* ]]; then
    if [[ "$APP_IMAGE" == "$EXPECTED_IMAGE" ]]; then
      [[ "$SCENARIO" != up_failure && "$SCENARIO" != rollback_failure && "$SCENARIO" != missing_previous && "$SCENARIO" != first_deploy ]]
    else
      [[ "$SCENARIO" != rollback_failure ]]
    fi
  elif [[ "$*" == 'image ls '* ]]; then
    printf '%s\n' "$EXPECTED_IMAGE" "$PREVIOUS" "$IMAGE_NAME:old" 'festa-backend:obsolete'
  fi
}
curl() {
  echo curl >> "$OCI_DEPLOY_PATH/calls"
  if [[ "$SCENARIO" == health_failure && ! -e "$OCI_DEPLOY_PATH/health-failed" ]]; then
    touch "$OCI_DEPLOY_PATH/health-failed"
    return 1
  fi
}
'''


class DeploymentTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name)
        self.addCleanup(self.temp.cleanup)
        self.image = 'ghcr.io/greedy-team/festa-backend:new-amd64-123-1'
        self.previous = 'festa-backend:previous'
        self.env = dict(os.environ, OCI_DEPLOY_PATH=self.path.as_posix(),
                        IMAGE_NAME='ghcr.io/greedy-team/festa-backend',
                        IMAGE_TAG='new-amd64-123-1', EXPECTED_IMAGE=self.image,
                        PREVIOUS=self.previous, SCENARIO='success')
        (self.path / 'app.env').touch()
        self.write('.last-successful-image', self.previous)

    def write(self, name, value):
        (self.path / name).write_text(value + '\n', encoding='utf-8')

    def read(self, name):
        return (self.path / name).read_text(encoding='utf-8').strip()

    def run_script(self, script, scenario='success', args=(), token=None):
        self.env['SCENARIO'] = scenario
        source = self.path / 'script.sh'
        source.write_text(MOCKS + '\n' + script, encoding='utf-8', newline='\n')
        return subprocess.run([BASH, str(source), *args], env=self.env,
                              input=token, text=True, encoding='utf-8', capture_output=True)

    def test_success_records_after_finalize_and_preserves_previous(self):
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)
        self.assertEqual(self.read('.deployment-in-progress'), self.image)
        self.assertEqual(self.run_script(REMOTE[1]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.image)
        self.assertFalse((self.path / '.deployment-in-progress').exists())
        calls = self.read('calls')
        self.assertNotIn('image rm ' + self.previous, calls)
        self.assertNotIn('image rm ' + self.image, calls)
        self.assertIn('image rm festa-backend:obsolete', calls)
        self.assertIn('image rm ghcr.io/greedy-team/festa-backend:old', calls)

    def test_ghcr_previous_is_preserved(self):
        previous = 'ghcr.io/greedy-team/festa-backend:previous-amd64-122-1'
        self.env['PREVIOUS'] = previous
        self.write('.last-successful-image', previous)
        self.write('.deployment-in-progress', self.image)
        self.assertEqual(self.run_script(REMOTE[1]).returncode, 0)
        self.assertNotIn('image rm ' + previous, self.read('calls'))

    def test_legacy_container_bootstraps_record(self):
        (self.path / '.last-successful-image').unlink()
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)

    def test_up_and_health_failure_roll_back_without_pull(self):
        for scenario in ('up_failure', 'health_failure'):
            with self.subTest(scenario=scenario):
                self.write('calls', '')
                self.assertNotEqual(self.run_script(REMOTE[0], scenario).returncode, 0)
                self.assertEqual(self.read('.last-successful-image'), self.previous)
                self.assertFalse((self.path / '.deployment-in-progress').exists())
                calls = self.read('calls')
                self.assertIn(self.previous + '|compose --env-file app.env up', calls)
                self.assertNotIn(' pull ', calls)

    def test_failed_rollback_keeps_failure_marker(self):
        self.assertNotEqual(self.run_script(REMOTE[0], 'rollback_failure').returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)
        self.assertEqual(self.read('.deployment-in-progress'), self.image)

    def test_missing_previous_does_not_attempt_rollback(self):
        self.assertNotEqual(self.run_script(REMOTE[0], 'missing_previous').returncode, 0)
        self.assertNotIn(self.previous + '|compose', self.read('calls'))

    def test_first_deploy_failure_has_no_success_record(self):
        (self.path / '.last-successful-image').unlink()
        self.assertNotEqual(self.run_script(REMOTE[0], 'first_deploy').returncode, 0)
        self.assertFalse((self.path / '.last-successful-image').exists())

    def test_mismatched_finalize_does_not_record_or_clean(self):
        self.write('.deployment-in-progress', 'unexpected')
        self.assertNotEqual(self.run_script(REMOTE[1]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)
        self.assertFalse((self.path / 'calls').exists())

    def test_pull_credentials_cleaned_on_success_and_failure(self):
        script = (ROOT / 'deploy/pull-image.sh').read_text(encoding='utf-8')
        for scenario in ('success', 'login_failure', 'pull_failure'):
            with self.subTest(scenario=scenario):
                self.write('calls', '')
                result = self.run_script(script, scenario, ('reader', self.image), 'test-only-token')
                self.assertEqual(result.returncode == 0, scenario == 'success')
                config = self.read('config-path')
                probe = subprocess.run([BASH, '-c', 'test ! -e "$1"', '--', config])
                self.assertEqual(probe.returncode, 0)
                self.assertNotIn('test-only-token', result.stdout + result.stderr + self.read('calls'))
                self.assertNotIn('|compose', self.read('calls'))
                if scenario == 'login_failure':
                    self.assertNotIn(' pull ', self.read('calls'))

    def test_ssh_transport_passes_token_on_stdin(self):
        step = WORKFLOW.split('      - name: OCI에서 비공개 이미지 다운로드\n')[1]
        step = step.split('      - name:')[0].split('        run: |\n')[1]
        helper = '''
export -f docker
ssh() { bash -c "${@: -1}"; }
'''
        self.env.update(GHCR_USERNAME='reader', GHCR_READ_TOKEN='test-only-token',
                        RUNNER_TEMP=self.path.as_posix(), OCI_PORT='22',
                        OCI_USER='test', OCI_HOST='test')
        # 스크립트는 저장소 루트에서 실행되는 workflow와 같은 상대 경로를 사용한다.
        result = self.run_script(helper + textwrap.dedent(step))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('login ghcr.io --username reader --password-stdin', self.read('calls'))
        self.assertIn('pull ' + self.image, self.read('calls'))
        self.assertNotIn('test-only-token', result.stdout + result.stderr + self.read('calls'))

    def run_prepare_step(self, **overrides):
        self.env.update(RUNNER_TEMP=self.path.as_posix(), DEPLOY_ENVIRONMENT='development',
                        DB_URL='jdbc:postgresql://postgres:5432/festa',
                        OCI_HOST_KEY='test ssh-ed25519 AAAA', OCI_SSH_PRIVATE_KEY='test-key',
                        ADMIN_JWT_SECRET='test-admin-secret', ADMIN_INITIAL_USERNAME='',
                        ADMIN_INITIAL_PASSWORD='',
                        API_DOMAINS='api.every-festa.com, dev-api.every-festa.com',
                        PUBLIC_BASE_URL='https://api.every-festa.com')
        self.env.update(overrides)
        return self.run_script(step_script('SSH와 실행 환경 준비'))

    def test_api_domains_is_written_to_app_env(self):
        result = self.run_prepare_step()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("API_DOMAINS='api.every-festa.com, dev-api.every-festa.com'",
                      (self.path / 'app.env').read_text(encoding='utf-8'))

    def test_missing_api_domains_stops_before_server(self):
        for value in ('', ' , ', '   '):
            with self.subTest(value=value):
                result = self.run_prepare_step(API_DOMAINS=value)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('API_DOMAINS', result.stderr)

    def test_api_domains_comma_without_space_stops_before_server(self):
        for value in ('api.every-festa.com,dev-api.every-festa.com', 'api.every-festa.com,',
                      'api.every-festa.com, '):
            with self.subTest(value=value):
                result = self.run_prepare_step(API_DOMAINS=value)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('API_DOMAINS', result.stderr)
                self.assertNotIn('API_DOMAINS', (self.path / 'app.env').read_text(encoding='utf-8'))

    def test_malformed_public_base_url_stops_before_server(self):
        for value in ('', 'http://api.every-festa.com', 'https://api.every-festa.com/',
                      'https://api.every-festa.com?x=1'):
            with self.subTest(value=value):
                result = self.run_prepare_step(PUBLIC_BASE_URL=value)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('PUBLIC_BASE_URL', result.stderr)
                self.assertNotIn('API_DOMAINS', (self.path / 'app.env').read_text(encoding='utf-8'))

    def test_public_base_url_domain_outside_api_domains_stops_before_server(self):
        # dev-api.every-festa.com은 api.every-festa.com을 부분 문자열로 품는다 — 포함 검색으로는 못 막는다
        for domains, base_url in (('dev-api.every-festa.com', 'https://api.every-festa.com'),
                                  ('api.every-festa.com', 'https://dev-api.every-festa.com')):
            with self.subTest(domains=domains, base_url=base_url):
                result = self.run_prepare_step(API_DOMAINS=domains, PUBLIC_BASE_URL=base_url)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('PUBLIC_BASE_URL', result.stderr)
                self.assertIn('API_DOMAINS', result.stderr)
                self.assertNotIn('API_DOMAINS', (self.path / 'app.env').read_text(encoding='utf-8'))

    def test_public_base_url_domain_matches_any_api_domain_ignoring_case(self):
        for base_url in ('https://dev-api.every-festa.com', 'https://Dev-API.every-festa.com'):
            with self.subTest(base_url=base_url):
                result = self.run_prepare_step(PUBLIC_BASE_URL=base_url)
                self.assertEqual(result.returncode, 0, result.stderr)

    def test_caddy_domains_are_wired_through_compose_and_workflow(self):
        caddyfile = (ROOT / 'deploy/Caddyfile').read_text(encoding='utf-8')
        compose = (ROOT / 'deploy/compose.yaml').read_text(encoding='utf-8')
        caddy_service = compose.split('\n  caddy:\n')[1].split('\nvolumes:\n')[0]
        self.assertIn('{$API_DOMAINS} {', caddyfile)
        self.assertNotIn('every-festa.com {', caddyfile)
        self.assertIn('API_DOMAINS: ${API_DOMAINS:?API_DOMAINS is required}', caddy_service)
        self.assertIn('API_DOMAINS: ${{ vars.API_DOMAINS }}', WORKFLOW)
        self.assertIn('write_env API_DOMAINS "$API_DOMAINS"', WORKFLOW)

    def run_https_check(self, base_url):
        self.env.update(PUBLIC_BASE_URL=base_url, OCI_HOST='129.225.160.27')
        record_curl = '''
curl() { printf '%s\\n' "$*" >> "$OCI_DEPLOY_PATH/curl-args"; }
'''
        return self.run_script(record_curl + step_script('HTTPS 서빙 확인'))

    def test_https_check_connects_to_deployed_server(self):
        result = self.run_https_check('https://api.every-festa.com')
        self.assertEqual(result.returncode, 0, result.stderr)
        args = self.read('curl-args')
        self.assertIn('--connect-to api.every-festa.com:443:129.225.160.27:443', args)
        self.assertIn('https://api.every-festa.com/actuator/health', args)

    def test_https_check_rejects_malformed_base_url(self):
        for value in ('', 'http://api.every-festa.com', 'https://api.every-festa.com/',
                      'https://api.every-festa.com?x=1'):
            with self.subTest(value=value):
                result = self.run_https_check(value)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('PUBLIC_BASE_URL', result.stderr)
                self.assertFalse((self.path / 'curl-args').exists())


if __name__ == '__main__':
    unittest.main()
