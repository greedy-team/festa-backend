"""릴리스 버전 동기화와 실제 워크플로우의 검사 대기를 검증한다.

python -B -m unittest discover -s .github/scripts -p 'test_*.py' -v
Bash와 jq가 필요하다. Windows에서는 JQ 환경변수로 실행 파일을 지정할 수 있다.
"""
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import textwrap
import unittest


ROOT = Path(__file__).resolve().parents[2]
VERSION_SCRIPT = ROOT / '.github/scripts/version_manager.py'
WORKFLOW = (ROOT / '.github/workflows/PROJECT-COMMON-RELEASE-CHANGELOG.yaml').read_text(encoding='utf-8')
BASH = 'C:/Program Files/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash')
JQ = os.environ.get('JQ') or shutil.which('jq')


class VersionTests(unittest.TestCase):
    def test_increment_updates_spring_dsl_and_modules(self):
        for filename in ('build.gradle', 'build.gradle.kts'):
            with self.subTest(filename=filename), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                (root / 'version.yml').write_text(
                    'version: "0.0.5"\nversion_code: 5\nproject_types: ["spring"]\n'
                    'project_paths:\n  spring: "."\n', encoding='utf-8')
                (root / filename).write_text('version = "0.0.1-SNAPSHOT"\n', encoding='utf-8')
                (root / 'module').mkdir()
                (root / 'module/build.gradle.kts').write_text('version = "0.0.5"\n', encoding='utf-8')
                result = subprocess.run(
                    [sys.executable, str(VERSION_SCRIPT), 'increment'], cwd=root,
                    env={**os.environ, 'PYTHONIOENCODING': 'utf-8'}, capture_output=True,
                    text=True, encoding='utf-8', check=True)
                self.assertEqual(result.stdout.strip().splitlines()[-1], '0.0.6')
                self.assertIn('version: "0.0.6"', (root / 'version.yml').read_text())
                self.assertEqual((root / filename).read_text(), 'version = "0.0.6"\n')
                self.assertEqual((root / 'module/build.gradle.kts').read_text(), 'version = "0.0.6"\n')

    def test_get_reads_kotlin_dsl_version(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'version.yml').write_text(
                'version: "0.0.5"\nproject_types: ["spring"]\n', encoding='utf-8')
            (root / 'build.gradle.kts').write_text('version = "0.0.6"\n', encoding='utf-8')
            result = subprocess.run(
                [sys.executable, str(VERSION_SCRIPT), 'get'], cwd=root,
                env={**os.environ, 'PYTHONIOENCODING': 'utf-8'}, capture_output=True,
                text=True, encoding='utf-8', check=True)
            self.assertEqual(result.stdout.strip().splitlines()[-1], '0.0.6')


@unittest.skipUnless(BASH and JQ, 'Bash와 jq가 필요합니다')
class CheckTests(unittest.TestCase):
    def run_checks(self, scenario):
        function = re.search(r'^          wait_required_checks\(\) \{\n.*?^          \}', WORKFLOW, re.M | re.S)
        self.assertIsNotNone(function)
        mock = r'''
        gh() {
          if [[ "$SCENARIO" == absent ]]; then
            echo '[]'
          elif [[ "$SCENARIO" == api_error ]]; then
            return 1
          elif [[ "$SCENARIO" == main && "$*" == *--required* ]]; then
            echo '[]'
            return 1
          elif [[ "$SCENARIO" == delayed && ! -e polled ]]; then
            echo '[{"name":"빌드 검증","bucket":"pending"}]'
          else
            bucket="$SCENARIO"
            [[ "$SCENARIO" == main || "$SCENARIO" == delayed ]] && bucket=pass
            printf '[{"name":"빌드 검증","bucket":"%s"}]\n' "$bucket"
          fi
        }
        sleep() { touch polled; }
        seq() { printf '1\n2\n'; }
        '''
        script = ('set -euo pipefail\n'
                  + f'jq() {{ {shlex.quote(Path(JQ).as_posix())} "$@"; }}\n'
                  + textwrap.dedent(mock) + '\n' + textwrap.dedent(function.group())
                  + '\nwait_required_checks 177\necho MERGE_ALLOWED\n')
        with tempfile.TemporaryDirectory() as directory:
            return subprocess.run(
                [BASH, '-c', script], cwd=directory,
                env={**os.environ, 'SCENARIO': scenario}, capture_output=True,
                text=True, encoding='utf-8', timeout=60)

    def test_required_checks_and_unprotected_main(self):
        for scenario in ('pass', 'main', 'delayed'):
            with self.subTest(scenario=scenario):
                result = self.run_checks(scenario)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn('MERGE_ALLOWED', result.stdout)

    def test_failure_missing_checks_and_api_errors_block_merge(self):
        for scenario in ('fail', 'cancel', 'skipping', 'absent', 'api_error'):
            with self.subTest(scenario=scenario):
                result = self.run_checks(scenario)
                self.assertNotEqual(result.returncode, 0)
                self.assertNotIn('MERGE_ALLOWED', result.stdout)


if __name__ == '__main__':
    unittest.main()
