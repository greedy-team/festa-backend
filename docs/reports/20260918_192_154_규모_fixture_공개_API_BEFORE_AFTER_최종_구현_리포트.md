# #192 #154 규모 fixture 공개 API BEFORE/AFTER 최종 구현 리포트

## 목적

#192는 #154 규모 데이터에서 #184/#185 변경 전후의 공개 API 성능을 같은 조건으로 재현·비교하는 작업이다. PR #202는 `develop`에 merge됐으며, production 또는 shared development에 부하를 주지 않는 isolated performance 환경과 그 재현 절차를 확정했다.

## 확정한 fixture와 workload

- 전용 PostgreSQL `festa_perf` 및 별도 named volume `festa-perf-postgres-data`만 사용한다. 배포 DB volume과 포트를 공유하지 않는다.
- fixture는 Host 200, Festival 20,000, Artist 30,000, ArtistAlias 45,000, Lineup 300,000으로 구성한다. Festival은 약 16,000건이 published 상태가 되도록 생성하고 `ANALYZE`한다.
- `performance/load/config.js`의 manifest가 Festival/Artist/Host detail ID와 Festival/Artist list page를 각각 4개씩 고정한다. 일반 호출은 이 값을 결정적으로 순환해 특정 ID 또는 page 0의 cache 편향을 피한다.
- 20개 query/type search corpus도 난수 없이 search 요청 순번으로 순환한다. `mixed`는 100-slot 비율을 유지하면서 endpoint별 호출 순번을 별도로 계산한다. 전체 mixed iteration을 그대로 쓰면 mixed slot 주기와 manifest 길이가 공약수를 가질 때 일부 ID/page만 반복될 수 있기 때문이다.
- `fixture-manifest` scenario를 warm-up과 비교 측정 전에 실행한다. 모든 detail ID, manifest page, search corpus query/type이 HTTP 200이면서 결과가 비어 있지 않아야 한다.

## 리뷰에서 보강한 재현성

- `FestivalPage=0`은 PowerShell의 false-like 값으로 탈락하지 않도록 빈 문자열 여부로 전달을 판단하도록 수정했다. 따라서 #185의 fixed list 조건 `page=0`을 그대로 재현할 수 있다.
- 희귀 host 검색 `PERF171`이 실제 결과를 갖도록 fixture의 host short name을 보장하고 SQL 검증을 추가했다.
- 기존 host=7/artist=38 결합 조건은 fixture 규칙상 0건이어서, 실제 공존하는 host=1/artist=21038 조합으로 바꾸고 3건 반환을 확인했다. fixture bootstrap도 이 결합 조건이 non-empty인지 실패 처리로 검증한다.
- manifest 도입 뒤 더는 참조되지 않는 `ids` 설정은 제거했다.
- runtime metrics 수집은 PostgreSQL `psql`에 `-U festa_perf -d festa_perf`를 명시하도록 고쳤다. 격리 performance container에서 끝까지 실행해 CPU, memory, DB connection sample이 생성되고 connection 값 6이 기록되는 것을 확인했다.
- README의 작성자 로컬 절대경로를 제거했다. `9ace799` (#184 포함, #185 제외) 기준 detached BEFORE worktree를 `git worktree add`로 만들고 그 migration directory를 지정하는 절차를 문서화했다.

## performance DB bootstrap과 Flyway 정책

`bootstrap.sh`는 baseline worktree의 migration SQL을 `psql`로 직접 적용한 뒤 non-Flyway fixture SQL을 넣는다. 따라서 초기 `festa_perf`에는 `flyway_schema_history`가 없다.

이 DB에 API application을 연결하는 이번 재현 절차는 `SPRING_FLYWAY_ENABLED=false` 및 `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`를 사용한다. BEFORE와 AFTER는 같은 volume을 유지하고 application image만 교체하며, 두 시리즈 사이에 `down -v`를 실행하지 않는다.

이는 과거 #185 AFTER 실행 당시의 Spring/Flyway 환경값을 확인해 복원한 기록이 아니다. 당시 k6 target, app commit, fixture 초기화 방식은 남아 있지만 실제 app 기동 명령과 DB/Flyway/JPA 환경변수는 보존되어 있지 않아 확인할 수 없다. 이번 리뷰에서 psql bootstrap 구조에 맞춰 명시한 재현 절차이며, 이 설정으로 격리 앱이 정상 기동하는 것을 확인했다.

## #185 BEFORE/AFTER 비교 결과

2026-09-17 isolated environment에서 2분 warm-up 후 k6 Docker runner, 5 RPS, size 20 조건으로 측정했다. raw k6 summary와 runtime samples는 Git에 넣지 않고 ignored `performance/load/results/`에 보관하는 원칙을 유지한다.

| 조건 | AFTER requests / errors | AFTER p50 | AFTER p95 | AFTER p99 | BEFORE 대비 p50 | BEFORE 대비 p95 | BEFORE 대비 p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Festival list `page=0&size=20&sort=LATEST` (3m) | 901 / 0 | 16.931 ms | 22.385 ms | 24.892 ms | -61.02% | -60.42% | -60.88% |
| Festival list `hostId=7` (1m) | 300 / 0 | 6.116 ms | 7.399 ms | 8.750 ms | -50.26% | -51.37% | -55.74% |
| Search `SpringCampusFestival11999` (1m) | 301 / 0 | 19.304 ms | 24.548 ms | 35.174 ms | -27.58% | -31.41% | -21.71% |

이 표는 #185 비교 조건의 API latency 결과다. local smoke 수치를 최종 TPS 결론으로 바꾸지 않았으며, raw artifact를 Git에 커밋하지 않는다.

## 최종 검증

- PR #202 CI: 712 tests, 754 runs 통과.
- `fixture-manifest` 실행에서 Festival/Artist/Host detail ID 각 4개, Festival/Artist page 각 4개, search corpus 20 query/type 모두 200 및 non-empty를 확인했다.
- runtime metrics collector를 isolated performance container에서 실제 실행했다.
- production과 shared development host는 wrapper와 scenario가 모두 거부한다. 최종 비교는 isolated temporary VM 및 독립 fixture volume을 대상으로 한다.

## 결론

#185 BEFORE/AFTER 측정은 결정적 fixture manifest, endpoint별 mixed 순번, non-empty 사전 검증, 명시적인 psql/Flyway 재현 정책을 갖춘 절차로 재현 가능해졌다. 다만 과거 AFTER application의 실제 Flyway 실행값은 보존 기록 부재로 미확인 사실을 그대로 유지한다.
