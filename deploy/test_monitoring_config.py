"""prometheus.yml의 job 이름과 대시보드가 조회하는 job 이름을 맞춰 둔다.

python -m unittest discover -s deploy -p 'test_*.py'

둘이 어긋나도 수집은 정상이고 알림도 나지 않는다 — 대시보드만 조용히 빈다.
2026-09-18 리뷰에서 실제로 잡힌 결함이라(대시보드는 job="postgres-exporter",
prometheus.yml은 job_name: postgres) 계약으로 고정한다.

도커가 필요 없어 어디서나 돈다.
"""
import re
import unittest
from pathlib import Path

DEPLOY = Path(__file__).resolve().parent
PROMETHEUS = DEPLOY / 'prometheus/prometheus.yml'
DASHBOARDS = sorted((DEPLOY / 'grafana/dashboards').glob('*.json'))


class MonitoringConfigTest(unittest.TestCase):
    def test_dashboards_only_reference_configured_jobs(self):
        configured = set(re.findall(r'^\s*-\s*job_name:\s*(\S+)',
                                    PROMETHEUS.read_text(encoding='utf-8'), re.M))
        self.assertTrue(configured, 'prometheus.yml에서 job_name을 읽지 못했다')
        self.assertTrue(DASHBOARDS, '대시보드가 하나도 없다')
        for path in DASHBOARDS:
            # JSON 안에서는 따옴표가 이스케이프돼 있다: job=\"postgres-exporter\"
            referenced = set(re.findall(r'job=~?\\?"([^"\\]+)',
                                        path.read_text(encoding='utf-8')))
            # $job 처럼 변수로 고르는 대시보드는 job 이름에 구애받지 않는다.
            hardcoded = {name for name in referenced if not name.startswith('$')}
            with self.subTest(dashboard=path.name):
                self.assertLessEqual(
                    hardcoded, configured,
                    f'{path.name}이 조회하는 job이 prometheus.yml에 없다. '
                    f'대시보드={sorted(hardcoded)} prometheus.yml={sorted(configured)}')


if __name__ == '__main__':
    unittest.main()
