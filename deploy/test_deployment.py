"""실제 워크플로우의 원격 Bash를 Docker/HTTP 대역으로 실행한다.

python -m unittest discover -s deploy -p 'test_*.py'
실제 컨테이너 기동과 GHCR 인증은 별도 검증한다.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
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
  elif [[ "$*" == *'--env-file app.env config' ]]; then
    # 실제 compose처럼 APP_IMAGE가 없으면 실패하고, 네트워크·볼륨의 들여쓴 name 줄도 함께 낸다.
    # 프로젝트 이름은 일부러 festa가 아니다 — 코드가 이름을 읽지 않고 박아 두면 드러난다.
    if [[ -z "${APP_IMAGE:-}" || "$SCENARIO" == *config_error ]]; then
      echo 'required variable APP_IMAGE is missing a value' >&2
      return 1
    fi
    if [[ "$SCENARIO" == *quoted_compose_project ]]; then
      echo 'name: "festa-e2"'
    elif [[ "$SCENARIO" != *no_compose_project ]]; then
      echo 'name: festa-e2'
    fi
    printf '%s\n' 'services:' '  app-blue:' '    image: app' 'networks:' '  default:' \
      '    name: festa-e2_default' 'volumes:' '  caddy-data:' '    name: festa-e2_caddy-data'
  elif [[ "$*" == *'up -d postgres caddy' ]]; then
    # Caddy가 (재)생성되는 순간 서버에 놓인 설정을 찍는다.
    printf 'caddy-up Caddyfile=%s active=%s\n' "$(cat "$OCI_DEPLOY_PATH/Caddyfile")" \
      "$(cat "$OCI_DEPLOY_PATH/upstream/active.caddy")" >> "$OCI_DEPLOY_PATH/calls"
  elif [[ "$*" == 'image inspect '* ]]; then
    [[ "$SCENARIO" != missing_previous || "$3" != "$PREVIOUS" ]]
  elif [[ "$1" == ps && ( "$2" == -q || "$2" == -aq ) && "$3" == --filter ]]; then
    # 프로젝트와 서비스 이름을 둘 다 좁힌 조회에만 기존 app이 나온다. 하나라도 빠지면
    # 다른 프로젝트의 app이나 같은 프로젝트의 다른 서비스까지 걸린다.
    if [[ "${*:3}" == '--filter label=com.docker.compose.project=festa-e2 --filter label=com.docker.compose.service=app' ]]; then
      # 첫 전환 전에만 기존 app 컨테이너가 남아 있다.
      if [[ "$SCENARIO" == first_switch* ]]; then
        echo legacy-container
      fi
    else
      echo other-project-app
      echo same-project-postgres
    fi
  elif [[ "$1" == inspect ]]; then
    echo "$PREVIOUS"
  elif [[ "$*" == *'ps -q app'* || "$*" == *'ps -a -q app'* ]]; then
    [[ "$SCENARIO" == first_deploy ]] || echo running-container
  elif [[ "$*" == *'up -d --wait'* ]]; then
    if [[ "$APP_IMAGE" == "$EXPECTED_IMAGE" ]]; then
      [[ "$SCENARIO" != *up_failure && "$SCENARIO" != rollback_failure && "$SCENARIO" != missing_previous && "$SCENARIO" != first_deploy ]]
    else
      [[ "$SCENARIO" != rollback_failure ]]
    fi
  elif [[ "$*" == *'exec -T caddy caddy reload'* ]]; then
    # 실제 exec는 -T여도 stdin을 끝까지 읽는다.
    cat >/dev/null
    [[ "$SCENARIO" != reload_failure ]]
  elif [[ "$*" == *'exec -T caddy wget'* ]]; then
    cat >/dev/null
    [[ "$SCENARIO" != caddy_probe_failure ]]
  elif [[ "$*" == 'image ls '* ]]; then
    printf '%s\n' "$EXPECTED_IMAGE" "$PREVIOUS" "$IMAGE_NAME:old" 'festa-backend:obsolete'
  fi
}
curl() {
  printf 'curl %s\n' "$*" >> "$OCI_DEPLOY_PATH/calls"
  if [[ "$SCENARIO" == health_failure && ! -e "$OCI_DEPLOY_PATH/health-failed" ]]; then
    touch "$OCI_DEPLOY_PATH/health-failed"
    return 1
  fi
  # 되살린 기존 app(8080)이 끝내 응답하지 않는 롤백 실패
  if [[ "$SCENARIO" == *rollback_health* && "$*" == *'localhost:8080/'* ]]; then
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
                        PREVIOUS=self.previous, SCENARIO='success',
                        # 값이 없으면 순차 배포다. 겹침을 보는 테스트만 overlap으로 덮어쓴다.
                        DEPLOY_STRATEGY='')
        (self.path / 'app.env').touch()
        self.write('.last-successful-image', self.previous)
        self.active('app-blue')
        self.write('Caddyfile', 'old caddyfile')
        self.transferred()

    def transferred(self):
        """설정 전송 스텝이 새 Caddyfile을 살아 있는 파일 옆에 놓은 상태를 만든다."""
        self.write('Caddyfile.next', 'new caddyfile')

    def write(self, name, value):
        (self.path / name).write_text(value + '\n', encoding='utf-8')

    def read(self, name):
        return (self.path / name).read_text(encoding='utf-8').strip()

    def active(self, service):
        """트래픽을 받는 색을 서버 상태 파일에 적는다. 이 파일이 없으면 첫 전환이다."""
        (self.path / 'upstream').mkdir(exist_ok=True)
        self.write('upstream/active.caddy', f'reverse_proxy {service}:8080')

    def calls(self):
        return self.read('calls').splitlines()

    def call_index(self, needle):
        for index, line in enumerate(self.calls()):
            if needle in line:
                return index
        self.fail(f'호출 기록에 없다: {needle}')

    def run_script(self, script, scenario='success', args=(), token=None):
        self.env['SCENARIO'] = scenario
        source = self.path / 'script.sh'
        source.write_text(MOCKS + '\n' + script, encoding='utf-8', newline='\n')
        # exec 대역이 stdin을 읽으므로 넘길 것이 없어도 빈 입력을 준다(부모 터미널에 매달리지 않게).
        stdin = ''
        if token is not None:
            stdin = token
        return subprocess.run([BASH, str(source), *args], env=self.env,
                              input=stdin, text=True, encoding='utf-8', capture_output=True)

    def run_remote_over_ssh(self, script, scenario='success'):
        """CD처럼 원격 스크립트를 bash -s의 stdin으로 흘려 넣는다. 파일로 돌리면 stdin을 먹는 명령이 드러나지 않는다."""
        self.env['SCENARIO'] = scenario
        return subprocess.run([BASH, '-s'], env=self.env, input=MOCKS + '\n' + script,
                              text=True, encoding='utf-8', capture_output=True)

    def test_success_records_after_finalize_and_preserves_previous(self):
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)
        self.assertEqual(self.read('.deployment-in-progress'), self.image)
        self.assertEqual(self.run_script(REMOTE[1]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.image)
        self.assertEqual(self.read('.previous-successful-image'), self.previous)
        self.assertFalse((self.path / '.deployment-in-progress').exists())
        calls = self.read('calls')
        self.assertNotIn('image rm ' + self.previous, calls)
        self.assertNotIn('image rm ' + self.image, calls)
        self.assertIn('image rm festa-backend:obsolete', calls)
        self.assertIn('image rm ghcr.io/greedy-team/festa-backend:old', calls)

    def test_deploy_switches_to_the_other_color(self):
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertEqual(self.read('upstream/active.caddy'), 'reverse_proxy app-green:8080')
        calls = self.read('calls')
        self.assertIn('up -d --wait --wait-timeout 300 app-green', calls)
        self.assertIn('exec -T caddy caddy reload', calls)
        # 새 색은 루프백 포트와 Caddy 안에서 두 번 확인한다.
        self.assertIn('exec -T caddy wget -q -O /dev/null http://app-green:8080/actuator/health', calls)

    def test_sequential_stops_old_color_before_starting_new_one(self):
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertLess(self.call_index('stop --timeout 40 app-blue'),
                        self.call_index('up -d --wait --wait-timeout 300 app-green'))

    def test_overlap_keeps_old_color_until_after_the_switch(self):
        self.env['DEPLOY_STRATEGY'] = 'overlap'
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertLess(self.call_index('up -d --wait --wait-timeout 300 app-green'),
                        self.call_index('exec -T caddy caddy reload'))
        self.assertLess(self.call_index('exec -T caddy caddy reload'),
                        self.call_index('stop --timeout 40 app-blue'))

    def test_first_switch_treats_legacy_app_as_current_color(self):
        (self.path / 'upstream/active.caddy').unlink()
        self.assertEqual(self.run_script(REMOTE[0], 'first_switch').returncode, 0)
        self.assertEqual(self.read('upstream/active.caddy'), 'reverse_proxy app-blue:8080')
        calls = self.read('calls')
        # compose.yaml에 app 서비스가 없으므로 라벨로 찾아 직접 멈춘다.
        self.assertIn('stop --time 40 legacy-container', calls)
        self.assertIn('up -d --wait --wait-timeout 300 app-blue', calls)

    def test_first_switch_failure_restarts_the_legacy_container(self):
        # compose.yaml에 app 서비스가 없으므로 up으로는 되살릴 수 없다. 멈춰 둔 컨테이너를 start 한다.
        (self.path / 'upstream/active.caddy').unlink()
        self.assertNotEqual(self.run_script(REMOTE[0], 'first_switch_up_failure').returncode, 0)
        calls = self.read('calls')
        self.assertIn('start legacy-container', calls)
        self.assertNotIn('up -d --wait --wait-timeout 300 app\n', calls + '\n')
        self.assertFalse((self.path / 'upstream/active.caddy').read_text(encoding='utf-8').startswith('reverse_proxy app-'))

    def test_finalize_removes_legacy_container_after_https_check(self):
        self.write('.deployment-in-progress', self.image)
        self.assertEqual(self.run_script(REMOTE[1], 'first_switch').returncode, 0)
        self.assertIn('rm -f legacy-container', self.read('calls'))

    def test_legacy_container_bootstraps_record(self):
        (self.path / '.last-successful-image').unlink()
        self.assertEqual(self.run_script(REMOTE[0]).returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)

    def test_first_switch_without_record_does_not_ask_compose_for_the_app_service(self):
        # compose.yaml에 app 서비스가 없어 'no such service'로 죽던 자리다. 라벨로 찾는다.
        (self.path / 'upstream/active.caddy').unlink()
        (self.path / '.last-successful-image').unlink()
        result = self.run_script(REMOTE[0], 'first_switch')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.read('.last-successful-image'), self.previous)
        self.assertNotIn('ps -q app\n', self.read('calls') + '\n')

    def test_legacy_lookup_never_touches_app_of_other_compose_projects(self):
        # 서비스 이름 라벨만으로 찾으면 다른 프로젝트의 app도, 프로젝트만으로 찾으면 같은 프로젝트의
        # 다른 서비스도 멈추고 지운다. 멈춤·되살림·삭제 셋 다 본다.
        (self.path / 'upstream/active.caddy').unlink()
        (self.path / '.last-successful-image').unlink()
        result = self.run_script(REMOTE[0], 'first_switch')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.run_script(REMOTE[1], 'first_switch').returncode, 0)
        calls = self.read('calls')
        self.assertIn('stop --time 40 legacy-container', calls)
        self.assertIn('rm -f legacy-container', calls)

        self.write('calls', '')
        (self.path / 'upstream/active.caddy').unlink()
        self.transferred()
        self.assertNotEqual(self.run_script(REMOTE[0], 'first_switch_up_failure').returncode, 0)
        self.assertIn('start legacy-container', self.read('calls'))
        for bystander in ('other-project-app', 'same-project-postgres'):
            self.assertNotIn(bystander, calls + self.read('calls'))

    def test_quoted_compose_project_name_is_unquoted_for_the_filter(self):
        # compose는 숫자처럼 보이는 프로젝트 이름을 따옴표로 감싸 낸다. 따옴표째 쓰면 기존 app을 못 찾는다.
        (self.path / 'upstream/active.caddy').unlink()
        result = self.run_script(REMOTE[0], 'first_switch_quoted_compose_project')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('stop --time 40 legacy-container', self.read('calls'))

    def test_unreadable_compose_project_stops_before_touching_containers(self):
        for scenario in ('first_switch_no_compose_project', 'first_switch_config_error'):
            with self.subTest(scenario=scenario):
                self.write('calls', '')
                (self.path / 'upstream/active.caddy').unlink(missing_ok=True)
                result = self.run_script(REMOTE[0], scenario)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('compose 프로젝트', result.stderr)
                calls = self.read('calls')
                self.assertNotIn('up -d', calls)
                self.assertNotIn('stop', calls)
                self.assertFalse((self.path / 'upstream/active.caddy').exists())
                self.assertFalse((self.path / '.deployment-in-progress').exists())
                # 여기서 멈춘 채 옛 Caddy가 재시작돼도 뜰 수 있게, 살아 있는 Caddyfile은 그대로다.
                self.assertEqual(self.read('Caddyfile'), 'old caddyfile')

    def test_caddy_is_brought_up_only_after_new_caddyfile_and_upstream_are_in_place(self):
        # 첫 전환 전의 Caddy에는 upstream 마운트가 없다. 새 Caddyfile을 놓았으면 곧바로 재생성해야
        # 재시작돼도 import 대상을 찾는다.
        for scenario in ('first_switch', 'success'):
            with self.subTest(scenario=scenario):
                self.write('calls', '')
                self.write('Caddyfile', 'old caddyfile')
                self.transferred()
                if scenario == 'first_switch':
                    (self.path / 'upstream/active.caddy').unlink()
                    upstream = 'reverse_proxy app:8080'
                else:
                    self.active('app-blue')
                    upstream = 'reverse_proxy app-blue:8080'
                result = self.run_script(REMOTE[0], scenario)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn(f'caddy-up Caddyfile=new caddyfile active={upstream}', self.read('calls'))
                self.assertFalse((self.path / 'Caddyfile.next').exists())

    def test_caddyfile_is_overwritten_in_place_for_the_single_file_bind_mount(self):
        # Caddy는 Caddyfile을 파일 하나로 마운트해 원래 inode에 묶인다. mv로 바꿔치면
        # 서버엔 새 내용이 보여도 caddy reload는 옛 내용을 읽는다.
        before = os.stat(self.path / 'Caddyfile').st_ino
        result = self.run_script(REMOTE[0])
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.read('Caddyfile'), 'new caddyfile')
        self.assertEqual(os.stat(self.path / 'Caddyfile').st_ino, before)

    def test_missing_new_caddyfile_stops_before_touching_anything(self):
        # 없는 채로 덮어쓰면 리다이렉트가 먼저 돌아 살아 있는 Caddyfile을 비운다.
        (self.path / 'upstream/active.caddy').unlink()
        (self.path / 'Caddyfile.next').unlink()
        self.write('calls', '')
        result = self.run_script(REMOTE[0], 'first_switch')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('Caddyfile.next', result.stderr)
        self.assertEqual(self.read('Caddyfile'), 'old caddyfile')
        calls = self.read('calls')
        self.assertNotIn('up -d', calls)
        self.assertNotIn('stop', calls)
        self.assertFalse((self.path / 'upstream/active.caddy').exists())
        self.assertFalse((self.path / '.deployment-in-progress').exists())

    def test_finalize_skips_only_legacy_cleanup_when_compose_project_is_unreadable(self):
        # 성공 기록은 HTTPS 확인 뒤라 그대로 남기고, 무엇을 지울지 모를 때는 컨테이너를 지우지 않는다.
        # 이미지 정리는 프로젝트 이름과 무관하므로 그대로 돈다.
        for scenario in ('first_switch_no_compose_project', 'first_switch_config_error'):
            with self.subTest(scenario=scenario):
                self.write('calls', '')
                self.write('.last-successful-image', self.previous)
                self.write('.deployment-in-progress', self.image)
                result = self.run_script(REMOTE[1], scenario)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.read('.last-successful-image'), self.image)
                calls = self.read('calls')
                self.assertNotIn('rm -f', calls)
                self.assertIn('image rm festa-backend:obsolete', calls)

    def test_first_switch_rollback_waits_as_long_as_the_other_rollback_path(self):
        # 기존 app은 docker start로 되살아나 기동을 기다려 주지 않는다. 개발 서버 기동이 128~190초라
        # 다른 롤백 경로(up --wait-timeout 300)와 같은 시간을 curl이 기다려야 한다.
        (self.path / 'upstream/active.caddy').unlink()
        self.assertNotEqual(self.run_script(REMOTE[0], 'first_switch_up_failure').returncode, 0)
        calls = self.calls()
        start = self.call_index('start legacy-container')
        health = next(line for line in calls[start:] if line.startswith('curl '))
        retries = re.search(r'--retry (\d+)', health)
        delay = re.search(r'--retry-delay (\d+)', health)
        self.assertIsNotNone(retries, health)
        self.assertIsNotNone(delay, health)
        window = int(retries.group(1)) * int(delay.group(1))
        max_time = re.search(r'--retry-max-time (\d+)', health)
        if max_time is not None:
            window = min(window, int(max_time.group(1)))
        self.assertGreaterEqual(window, 300, health)
        # --retry-max-time은 진행 중인 요청을 끊지 못한다. 요청마다 상한이 있어야 300초가 상한이 된다.
        self.assertRegex(health, r' --max-time \d+')
        self.assertIn('--retry-all-errors', health)
        self.assertIn('http://localhost:8080/actuator/health', health)

    def test_first_switch_rollback_that_never_becomes_healthy_keeps_failure_marker(self):
        (self.path / 'upstream/active.caddy').unlink()
        result = self.run_script(REMOTE[0], 'first_switch_rollback_health_up_failure')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('start legacy-container', self.read('calls'))
        self.assertEqual(self.read('.deployment-in-progress'), self.image)

    def test_remote_script_fed_through_stdin_still_switches(self):
        # CD는 원격 스크립트를 ssh의 bash -s로 stdin에 흘려 넣는다. exec가 그 stdin을 먹으면
        # 남은 스크립트가 사라져 전환 없이 성공(0)으로 끝난다.
        for strategy in ('', 'overlap'):
            with self.subTest(strategy=strategy):
                self.write('calls', '')
                self.active('app-blue')
                self.transferred()
                self.env['DEPLOY_STRATEGY'] = strategy
                result = self.run_remote_over_ssh(REMOTE[0])
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(self.read('upstream/active.caddy'), 'reverse_proxy app-green:8080')
                calls = self.read('calls')
                self.assertIn('exec -T caddy caddy reload', calls)
                self.assertIn('stop --timeout 40 app-blue', calls)

    def test_up_and_health_failure_roll_back_without_pull(self):
        for scenario in ('up_failure', 'health_failure', 'caddy_probe_failure'):
            with self.subTest(scenario=scenario):
                self.write('calls', '')
                self.active('app-blue')
                self.transferred()
                self.assertNotEqual(self.run_script(REMOTE[0], scenario).returncode, 0)
                self.assertEqual(self.read('.last-successful-image'), self.previous)
                self.assertFalse((self.path / '.deployment-in-progress').exists())
                calls = self.read('calls')
                self.assertIn(self.previous + '|compose --env-file app.env up', calls)
                self.assertNotIn(' pull ', calls)
                # 전환은 일어나지 않는다.
                self.assertEqual(self.read('upstream/active.caddy'), 'reverse_proxy app-blue:8080')

    def test_overlap_failure_leaves_traffic_on_old_color_without_rollback(self):
        self.env['DEPLOY_STRATEGY'] = 'overlap'
        self.assertNotEqual(self.run_script(REMOTE[0], 'up_failure').returncode, 0)
        calls = self.read('calls')
        self.assertEqual(self.read('upstream/active.caddy'), 'reverse_proxy app-blue:8080')
        self.assertIn('stop --timeout 40 app-green', calls)
        # 옛 색은 계속 떠 있으므로 이전 이미지로 되살릴 일이 없다.
        self.assertNotIn(self.previous + '|compose --env-file app.env up', calls)
        self.assertFalse((self.path / '.deployment-in-progress').exists())

    def test_reload_failure_restores_active_color_and_stops_target(self):
        self.env['DEPLOY_STRATEGY'] = 'overlap'
        self.assertNotEqual(self.run_script(REMOTE[0], 'reload_failure').returncode, 0)
        self.assertEqual(self.read('upstream/active.caddy'), 'reverse_proxy app-blue:8080')
        self.assertFalse((self.path / '.active.caddy.previous').exists())
        self.assertIn('stop --timeout 40 app-green', self.read('calls'))
        self.assertFalse((self.path / '.deployment-in-progress').exists())

    def test_failed_rollback_keeps_failure_marker(self):
        self.assertNotEqual(self.run_script(REMOTE[0], 'rollback_failure').returncode, 0)
        self.assertEqual(self.read('.last-successful-image'), self.previous)
        self.assertEqual(self.read('.deployment-in-progress'), self.image)

    def test_missing_previous_does_not_attempt_rollback(self):
        self.assertNotEqual(self.run_script(REMOTE[0], 'missing_previous').returncode, 0)
        self.assertNotIn(self.previous + '|compose --env-file app.env up -d --wait',
                         self.read('calls'))

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
                        PUBLIC_BASE_URL='https://api.every-festa.com',
                        PG_EXPORTER_DSN='', GRAFANA_ADMIN_PASSWORD='')
        self.env.update(overrides)
        return self.run_script(step_script('SSH와 실행 환경 준비'))

    def test_monitoring_secrets_are_written_to_app_env(self):
        # 서버에서 손으로 넣으면 이 스텝이 app.env를 새로 만들어 덮으므로 다음 배포에 사라진다.
        result = self.run_prepare_step(PG_EXPORTER_DSN='postgresql://m:p@postgres:5432/festa',
                                       GRAFANA_ADMIN_PASSWORD='s3cret')
        self.assertEqual(result.returncode, 0, result.stderr)
        app_env = (self.path / 'app.env').read_text(encoding='utf-8')
        self.assertIn("PG_EXPORTER_DSN='postgresql://m:p@postgres:5432/festa'", app_env)
        self.assertIn("GRAFANA_ADMIN_PASSWORD='s3cret'", app_env)

    def test_missing_monitoring_secrets_do_not_stop_the_deploy(self):
        # compose가 둘 다 ${...:-} 로 선언한다(DEC-0185). 비면 해당 컨테이너만 못 뜨고
        # 배포 전체는 계속돼야 한다 — 관측 비밀 하나로 앱 배포가 막히면 안 된다.
        result = self.run_prepare_step()
        self.assertEqual(result.returncode, 0, result.stderr)
        app_env = (self.path / 'app.env').read_text(encoding='utf-8')
        self.assertIn("PG_EXPORTER_DSN=''", app_env)
        self.assertIn("GRAFANA_ADMIN_PASSWORD=''", app_env)

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

    def test_caddy_upstream_is_server_state_not_a_fixed_service(self):
        caddyfile = (ROOT / 'deploy/Caddyfile').read_text(encoding='utf-8')
        compose = (ROOT / 'deploy/compose.yaml').read_text(encoding='utf-8')
        caddy_service = compose.split('\n  caddy:\n')[1].split('\nvolumes:\n')[0]
        # 색을 Caddyfile에 박으면 배포가 설정 파일을 덮어쓸 때 전환 상태가 사라진다.
        self.assertIn('import /etc/caddy/upstream/active.caddy', caddyfile)
        self.assertNotIn('reverse_proxy app', caddyfile)
        self.assertIn('./upstream:/etc/caddy/upstream:ro', caddy_service)
        # CD는 설정 파일만 보내고 active.caddy는 서버에서 만든다.
        transfer = WORKFLOW.split('      - name: OCI 인스턴스로 설정 전송\n')[1].split('      - name:')[0]
        self.assertNotIn('active.caddy', transfer)

    def test_switch_back_script_is_sent_to_the_deploy_path(self):
        # README는 서버의 배포 경로에서 'bash switch-back.sh'를 안내한다. CD가 보내지 않으면 서버에 없다.
        helper = '''
ssh() { :; }
scp() { printf 'scp %s\\n' "$*" >> "$OCI_DEPLOY_PATH/calls"; }
'''
        self.env.update(RUNNER_TEMP=self.path.as_posix(), OCI_PORT='22', OCI_USER='deploy', OCI_HOST='server')
        result = self.run_script(helper + step_script('OCI 인스턴스로 설정 전송'))
        self.assertEqual(result.returncode, 0, result.stderr)
        sent = [line.split() for line in self.calls() if line.startswith('scp ')]
        to_deploy_path = [args for args in sent if args[-1] == f'deploy@server:{self.path.as_posix()}/']
        self.assertEqual(len(to_deploy_path), 1, sent)
        self.assertIn('deploy/switch-back.sh', to_deploy_path[0])
        self.assertIn('cd /opt/festa && bash switch-back.sh', (ROOT / 'deploy/README.md').read_text(encoding='utf-8'))

    def test_monitoring_config_directories_are_sent(self):
        # compose의 monitoring 프로파일이 ./prometheus와 ./grafana를 bind mount한다.
        # CD가 보내지 않으면 서버에서 프로파일을 켜도 컨테이너가 뜨지 못한다.
        # scp는 파일만 보내므로 디렉터리는 tar로 간다 — 받는 쪽 ssh만 골라 stdin을 받는다.
        helper = '''
scp() { :; }
ssh() {
  case "$*" in
    *"tar -xz"*) cat > "$OCI_DEPLOY_PATH/sent.tar.gz" ;;
    *) : ;;
  esac
}
'''
        self.env.update(RUNNER_TEMP=self.path.as_posix(), OCI_PORT='22', OCI_USER='deploy', OCI_HOST='server')
        result = self.run_script(helper + step_script('OCI 인스턴스로 설정 전송'))
        self.assertEqual(result.returncode, 0, result.stderr)
        with tarfile.open(self.path / 'sent.tar.gz') as sent:
            names = sent.getnames()
        for expected in ('prometheus/prometheus.yml',
                         'grafana/provisioning/datasources/prometheus.yml',
                         'grafana/provisioning/dashboards/dashboards.yml'):
            self.assertIn(expected, names)
        self.assertTrue([n for n in names
                         if n.startswith('grafana/dashboards/') and n.endswith('.json')], names)

    def test_prometheus_rereads_the_config_after_it_is_sent(self):
        # 파일만 보내면 Prometheus는 읽지 않는다 — 기동할 때 한 번 읽고 그 뒤로는 SIGHUP
        # 때만 다시 읽는다. CD는 관측 스택을 재기동하지 않으므로(profiles: [monitoring])
        # 여기서 읽히지 않으면 새 스크랩 대상이 영원히 반영되지 않는다. 배포는 성공하고
        # 에러도 없이 화면만 조용히 빈다 — #207에서 잡힌 job 이름 불일치와 같은 종류다.
        helper = '''
scp() { :; }
docker() {
  printf '%s
' "$*" >> "$OCI_DEPLOY_PATH/docker-calls"
  case "$*" in
    *'ps -q prometheus') printf '%s' "${PROM_CID:-}" ;;
  esac
}
ssh() {
  case "$*" in
    *"tar -xz"*) cat > /dev/null ;;
    *"docker compose"*) ( eval "${!#}" ) ;;
    *) : ;;
  esac
}
'''
        self.env.update(RUNNER_TEMP=self.path.as_posix(), OCI_PORT='22', OCI_USER='deploy',
                        OCI_HOST='server', PROM_CID='prom-123')
        result = self.run_script(helper + step_script('OCI 인스턴스로 설정 전송'))
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = (self.path / 'docker-calls').read_text(encoding='utf-8')
        self.assertIn('kill -s HUP prom-123', calls)

    def test_prometheus_is_found_through_compose_not_by_service_label_alone(self):
        # 서비스 이름만으로 좁히면 다른 compose 프로젝트의 prometheus가 걸린다.
        # ISS-0154가 app에서 겪은 함정이고, 여기서 틀리면 남의 컨테이너에 신호를 보낸다.
        helper = '''
scp() { :; }
docker() {
  printf '%s
' "$*" >> "$OCI_DEPLOY_PATH/docker-calls"
  case "$*" in
    *'ps -q prometheus') printf '%s' "${PROM_CID:-}" ;;
  esac
}
ssh() {
  case "$*" in
    *"tar -xz"*) cat > /dev/null ;;
    *"docker compose"*) ( eval "${!#}" ) ;;
    *) : ;;
  esac
}
'''
        self.env.update(RUNNER_TEMP=self.path.as_posix(), OCI_PORT='22', OCI_USER='deploy',
                        OCI_HOST='server', PROM_CID='prom-123')
        self.run_script(helper + step_script('OCI 인스턴스로 설정 전송'))
        calls = (self.path / 'docker-calls').read_text(encoding='utf-8')
        self.assertIn('--profile monitoring ps -q prometheus', calls)
        self.assertNotIn('ps -q -f label=', calls)
        self.assertNotIn('ps -q --filter label=com.docker.compose.service=prometheus', calls)

    def test_deploy_continues_when_the_monitoring_stack_is_not_running(self):
        # 개발 E2에는 관측 스택이 없다(1GB라 안 들어간다). 없다고 배포가 멈추면 안 된다.
        helper = '''
scp() { :; }
docker() {
  printf '%s
' "$*" >> "$OCI_DEPLOY_PATH/docker-calls"
  case "$*" in
    *'ps -q prometheus') printf '%s' "${PROM_CID:-}" ;;
  esac
}
ssh() {
  case "$*" in
    *"tar -xz"*) cat > /dev/null ;;
    *"docker compose"*) ( eval "${!#}" ) ;;
    *) : ;;
  esac
}
'''
        self.env.update(RUNNER_TEMP=self.path.as_posix(), OCI_PORT='22', OCI_USER='deploy',
                        OCI_HOST='server', PROM_CID='')
        result = self.run_script(helper + step_script('OCI 인스턴스로 설정 전송'))
        self.assertEqual(result.returncode, 0, result.stderr)
        # 리로드 자체가 없는 경우에도 이 테스트의 주장(배포가 멈추지 않는다)은 성립한다.
        # 파일 부재로 에러를 내면 주장이 흐려지므로 없으면 빈 문자열로 본다.
        log = self.path / 'docker-calls'
        calls = log.read_text(encoding='utf-8') if log.exists() else ''
        self.assertNotIn('kill -s HUP', calls)

    def test_caddyfile_is_sent_beside_the_live_one_not_over_it(self):
        # 제자리에 덮으면, 교체 스텝이 Caddy를 재생성하기 전에 멈춘 뒤 옛 Caddy가 재시작될 때
        # upstream 마운트 없이 새 Caddyfile의 import를 못 찾아 뜨지 못한다. 적용은 교체 스텝이 한다.
        runner = self.path / 'runner'
        runner.mkdir()
        (runner / 'app.env').touch()
        helper = '''
ssh() { :; }
scp() {
  local args=()
  while (($#)); do
    case "$1" in -i|-o|-P) shift 2 ;; *) args+=("$1"); shift ;; esac
  done
  cp "${args[@]:0:${#args[@]}-1}" "${args[-1]#*:}"
}
'''
        self.env.update(RUNNER_TEMP=runner.as_posix(), OCI_PORT='22', OCI_USER='deploy', OCI_HOST='server')
        result = self.run_script(helper + step_script('OCI 인스턴스로 설정 전송'))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.read('Caddyfile'), 'old caddyfile')
        for sent, source in (('Caddyfile.next', 'deploy/Caddyfile'), ('compose.yaml', 'deploy/compose.yaml')):
            self.assertEqual((self.path / sent).read_text(encoding='utf-8'),
                             (ROOT / source).read_text(encoding='utf-8'))

    def test_compose_defines_two_colors_that_do_not_share_ports_or_log_files(self):
        compose = (ROOT / 'deploy/compose.yaml').read_text(encoding='utf-8')
        blue = compose.split('\n  app-blue:\n')[1].split('\n  app-green:\n')[0]
        green = compose.split('\n  app-green:\n')[1].split('\n  caddy:\n')[0]
        self.assertIn('"127.0.0.1:8081:8080"', blue)
        self.assertIn('"127.0.0.1:8082:8080"', green)
        self.assertIn('LOGGING_FILE_NAME: /app/logs/festa-blue.log', blue)
        self.assertIn('LOGGING_FILE_NAME: /app/logs/festa-green.log', green)
        # 옛 색을 멈출 때 진행 중 요청을 마무리할 시간(graceful shutdown 최대 30초)을 준다.
        self.assertIn('stop_grace_period: 40s', compose)
        self.assertNotIn('\n  app:\n', compose)

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
