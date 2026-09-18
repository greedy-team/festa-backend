# #192 공개 API AFTER 부하 측정

## 환경

- 2026-09-17, Docker Desktop 29.7.2, localhost only (`127.0.0.1:18080`, PostgreSQL `127.0.0.1:55432`)
- `a3aec76` application image; isolated `performance_festa-perf-postgres-data` volume
- Fixture: Host 200, Festival 20,000, Artist 30,000, ArtistAlias 45,000, Lineup 300,000
- k6 0.54.0 Docker runner, constant arrival rate 5 RPS, preallocated/max 2 VUs, size 20; each condition warmed for 2 minutes.

## AFTER

| Condition | Duration | Requests | Errors | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `GET /api/festivals?page=0&size=20&sort=LATEST` | 3m | 901 | 0 | 16.931 ms | 22.385 ms | 24.892 ms |
| `hostId=7` | 1m | 300 | 0 | 6.116 ms | 7.399 ms | 8.750 ms |
| `q=SpringCampusFestival11999` | 1m | 301 | 0 | 19.304 ms | 24.548 ms | 35.174 ms |

## BEFORE 대비 변화율

| Condition | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| 일반 | -61.02% | -60.42% | -60.88% |
| hostId=7 | -50.26% | -51.37% | -55.74% |
| q | -27.58% | -31.41% | -21.71% |

## 검증

- `fixture-manifest` 실제 실행 성공: festival/artist/host detail ID 각 4개, festival/artist page 각 4개, search corpus 20개 query/type 모두 200 및 결과 1건 이상.
- raw k6 summary와 runtime CPU/memory/DB connection samples are retained under ignored `performance/load/results/`.
