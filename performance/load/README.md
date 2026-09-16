# Public API load test (Issue #188)

This is an independent [k6](https://grafana.com/docs/k6/latest/) tool. It is not a Gradle dependency and it never changes application code, Flyway migrations, or the Issue #154 query measurements.

It targets public read APIs only:

| `SCENARIO` | Endpoint |
| --- | --- |
| `upcoming` | `GET /api/festivals/upcoming?limit=10` |
| `recent` | `GET /api/festivals/recent?limit=10` |
| `festivals` | `GET /api/festivals?page=0&size=20&sort=LATEST` |
| `festival-detail` | `GET /api/festivals/{id}` |
| `artists` | `GET /api/artists?page=0&size=20&sort=NAME` |
| `artist-detail` | `GET /api/artists/{id}` |
| `search` | `GET /api/search?q={query}&type={type}` |
| `host-detail` | `GET /api/hosts/{id}` (optional supporting scenario) |
| `mixed` | Fixed 100-slot public browse/search cycle |

`mixed` is deterministic: upcoming 12%, recent 10%, Festival list 18%, Festival detail 12%, Artist list 18%, Artist detail 10%, and search 20%. It intentionally does not include a write or administrator endpoint.

## Safety boundary

Never target `https://api.every-festa.com` (production) or `https://dev-api.every-festa.com` (the shared development server). `run.ps1` rejects both hosts. Final measurement uses an isolated temporary VM and an independent fixture volume.

The local stack below is for smoke/contract validation only. It is not a TPS result, because its Docker Desktop hardware, direct-app route, and minimal data are different from the final environment.

## Fixture and deterministic IDs

`FIXTURE=performance` is the default final-measurement profile and matches Issue #154's fixture scale:

| Table | Rows |
| --- | ---: |
| Host | 200 |
| Festival | 20,000 |
| Artist | 30,000 |
| ArtistAlias | 45,000 |
| Lineup | 300,000 |

Its canonical detail IDs are Festival/Artist/Host `1`. Override them with `FESTIVAL_ID`, `ARTIST_ID`, and `HOST_ID` if a rebuilt fixture uses different IDs.

`FIXTURE=smoke` uses the isolated local seed and IDs `900001`. The local seed includes the same canonical search strings as the performance fixture but is deliberately tiny.

## Search corpus

The 20-entry corpus is chosen by `iterationInTest % 20`; no random source or changing seed is used.

| Bucket | Slots | Ratio | Examples | API `type` |
| --- | ---: | ---: | --- | --- |
| Common 1-character | 6 | 30% | `김`, `축`, `성` | `ALL`, `ARTIST`, `FESTIVAL`, `HOST` |
| Common 2-character | 6 | 30% | `김아`, `축제`, `성능` | `ALL`, `ARTIST`, `FESTIVAL`, `HOST` |
| Rare | 3 | 15% | `희귀 아티스트 30000`, `희귀 검색 축제 20000`, `PERF171` | single type |
| Space-normalized | 3 | 15% | `서울축제`, `성능대학교` | `ALL`, `FESTIVAL`, `HOST` |
| English | 2 | 10% | `campus`, `spring` | `ARTIST`, `FESTIVAL` |

Use `-SearchBucket rare` or `-SearchType ARTIST` through the wrapper to isolate a corpus subset. The full corpus must be used for mixed/final runs.

## Load stages

| `STAGE` | Executor | Starting point | Duration |
| --- | --- | ---: | ---: |
| `smoke` | 1 VU, shared iterations | 100 iterations | max 2 min |
| `baseline` | constant arrival rate | 5 RPS | 3 min |
| `normal` | constant arrival rate | 15 RPS | 5 min |
| `stress` | constant arrival rate | 30 RPS | 5 min |
| `saturation` | constant arrival rate | 50 RPS | 5 min |

The latter four values are conservative starting rates, not a fixed maximum TPS or an acceptance target. Set `RATE`, `DURATION`, `PRE_ALLOCATED_VUS`, and `MAX_VUS` only after the temporary VM's CPU/RAM/disk/network snapshot is recorded. A final sustained maximum is the highest rate that meets the agreed latency/error conditions for at least three minutes; this script does not silently declare one.

## Run with installed k6

Install k6 using its official distribution, then provide the target explicitly:

```powershell
cd performance/load
$env:BASE_URL = 'http://localhost:18080'
.\run.ps1 -Stage smoke -Scenario mixed -Fixture smoke -EnvironmentName local-smoke
```

`BASE_URL` may instead be passed as `-BaseUrl`. The script writes a timestamped directory under `results/` with:

- `run-metadata.json`: run ID, time, stage, scenario, fixture, target, git SHA, optional image SHA, runner, and exit code.
- `k6-summary.json`: overall RPS/p50/p95/p99/error/check metrics and endpoint-tagged metrics.

Run artifacts are ignored by Git. Keep only reviewed result summaries or selected plans in a later report; do not commit raw run output by default.

## Run k6 in Docker

The wrapper can pull and run a pinned k6 image; no backend dependency is added:

```powershell
cd performance/load
.\run.ps1 -Runner docker -BaseUrl 'http://host.docker.internal:18080' -Stage smoke -Scenario mixed -Fixture smoke -EnvironmentName local-smoke
```

For an individual endpoint, for example:

```powershell
.\run.ps1 -Runner docker -BaseUrl 'http://host.docker.internal:18080' -Stage baseline -Scenario search -Fixture performance -SearchBucket rare -Rate 5 -Duration 3m
```

## Local Docker smoke stack

`smoke-up.ps1` builds the current branch's `bootJar`, makes a temporary image context under `.smoke-image/`, starts only an isolated PostgreSQL/app compose project, and seeds minimal deterministic data. It does not use the deployment volume or the Issue #154 fixture volume.

```powershell
cd performance/load
.\smoke-up.ps1
.\run.ps1 -Runner docker -BaseUrl 'http://host.docker.internal:18080' -Stage smoke -Scenario mixed -Fixture smoke -EnvironmentName local-smoke
.\smoke-down.ps1 -RemoveVolume
```

The cleanup command affects only the `festa-load-smoke` compose project and its named volume. It never targets a shared development or production stack.

## Metrics and thresholds

Every request has an `endpoint` tag; search additionally has `search_bucket` and `search_type`. The saved summary separates endpoint RPS, p50, p95, p99, HTTP error rate, and response-check failure count. k6 also exposes VU gauges.

When an endpoint has multiple tag series, its request count, RPS, HTTP error rate, response-check failures, and average latency are aggregated across those series. k6 does not provide the underlying histogram to `handleSummary`, so p50/p95/p99 are `null` for such an aggregate rather than presenting an inexact percentile. Single-series endpoints retain their exact k6 percentiles.

The defined thresholds are deliberately only safety/contract guards:

- HTTP failures below 1%
- checks above 99%
- p99 below 10 seconds (a hung-request guard)

They are not final performance acceptance criteria. Final reports must show actual RPS/TPS, p50/p95/p99, errors, VUs/arrival rate, app/PostgreSQL CPU and memory, DB connections, and available JVM/GC/Hikari observations.

## Final temporary-VM run

Before final measurement:

1. Merge PR #184 and PR #185, then build the current `develop` image.
2. Record temporary VM hardware, OS, Docker/container limits, JVM settings, PostgreSQL settings, disk/volume size, and k6 runner/network location.
3. Recreate the Issue #154 fixture in a separate PostgreSQL volume and use `FIXTURE=performance`.
4. Run smoke, then baseline → normal → stress → saturation. Repeat stable stages and retain the result directories outside Git.

Do not treat this branch's local smoke as the final API-level proof of the #184/#185 optimizations.
