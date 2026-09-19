# Public API load test (Issue #188)

This is an independent [k6](https://grafana.com/docs/k6/latest/) tool. It is not a Gradle dependency and it never changes application code, Flyway migrations, or the Issue #154 query measurements.

It targets public read APIs only:

| `SCENARIO` | Endpoint |
| --- | --- |
| `upcoming` | `GET /api/festivals/upcoming?limit=10` |
| `recent` | `GET /api/festivals/recent?limit=10` |
| `festivals` | `GET /api/festivals?page={manifest page}&size=20&sort=LATEST` |
| `festival-detail` | `GET /api/festivals/{id}` |
| `artists` | `GET /api/artists?page=0&size=20&sort=NAME` |
| `artist-detail` | `GET /api/artists/{id}` |
| `search` | `GET /api/search?q={query}&type={type}` |
| `host-detail` | `GET /api/hosts/{id}` (optional supporting scenario) |
| `fixture-manifest` | fixture detail IDs, list pages, and every search corpus query/type verification |
| `mixed` | Fixed 100-slot public browse/search cycle |

`mixed` is deterministic: upcoming 12%, recent 10%, Festival list 18%, Festival detail 12%, Artist list 18%, Artist detail 10%, and search 20%. Each endpoint has its own mixed request ordinal, so every manifest detail ID and page cycles independently even when the 100-slot mixed cycle shares a divisor with a manifest length. It intentionally does not include a write or administrator endpoint.

## Safety boundary

`https://dev-api.every-festa.com` (the shared development server) is always blocked. `https://api.every-festa.com` (A1 production) is also blocked by default in both `run.ps1` and `scenario.js`; the only exception is the explicit `A1_READ_ONLY_LOAD_TEST` approval combined with the bounded profile below. The duplicate checks mean direct `k6 run` cannot bypass the boundary.

### A1 production controlled run

A1 production is limited to a separately validated A1 target manifest and one manually started profile at a time. `step` permits only **1, 5, 10, 25, 50, 100, 150, 200, 250, or 300 RPS** for at most **3 minutes**. `deployment-experiment` permits only **50 RPS for exactly 5 minutes**. There is no command that advances to another rate or performs a deployment.

For a mixed run, the wrapper derives VUs from the arrival-rate executor rather than accepting an arbitrary override: `preAllocatedVUs = rate` and `maxVUs = rate × 10`. A mixed iteration makes exactly one HTTP request, so this covers one second of initial concurrency and the existing 10-second p99 guard; the 300 RPS maximum is therefore 300 preallocated and 3000 maximum VUs. VUs do not raise the configured arrival rate.

`observation` and `deployment-recovery` are 0-RPS Grafana observation windows: 3 and 5 minutes respectively. They make no HTTP request and write only metadata. Use them for the baseline/recovery windows, not a mixed scenario with a zero rate.

### Mandatory A1 target validation

Do **not** use `FIXTURE=performance` on A1. Its IDs, pages, and search corpus describe the local Issue #154 synthetic fixture, not A1 data. A team member who can inspect Grafana and server state must copy `a1-manifest.example.json` to the ignored `a1-manifest.json` and enter only targets confirmed to be valid on A1. No production values are tracked in this repository.

Before every A1 mixed run, validate every A1 manifest detail ID, list page, and search query/type. This profile requires the same `A1_READ_ONLY_LOAD_TEST` approval as mixed load, exactly one VU and one iteration, disallows a configured rate, duration, or VU override, and spaces requests by one second. It is validation only, not a load test.

```powershell
.\run.ps1 -Runner docker -BaseUrl 'https://api.every-festa.com' -A1ProductionLoadApproval A1_READ_ONLY_LOAD_TEST `
  -Stage a1-manifest -Scenario fixture-manifest -Fixture a1 -A1ManifestFile .\a1-manifest.json `
  -EnvironmentName a1-grafana
```

The command succeeds only when every target returns HTTP 200 and a normal non-empty result. Save the resulting `results\<run-id>\run-metadata.json`. A1 `mixed` refuses to start unless `-A1ManifestValidationResult` points to successful validation metadata for the identical URL and manifest SHA-256. If validation fails, changes, or cannot be observed safely, **do not run A1 mixed load**; correct or replace the A1-only manifest and validate again. `dev-api.every-festa.com` remains blocked even with this approval.

Use `-BaseUrl` explicitly (an inherited `BASE_URL` is not accepted for A1), then inspect Grafana and the completed summary before a person chooses the next command:

```powershell
.\run.ps1 -Runner docker -BaseUrl 'https://api.every-festa.com' -A1ProductionLoadApproval A1_READ_ONLY_LOAD_TEST `
  -Stage baseline -Scenario mixed -A1Profile step -Fixture a1 -A1ManifestFile .\a1-manifest.json `
  -A1ManifestValidationResult .\results\<validated-run-id>\run-metadata.json `
  -EnvironmentName a1-grafana -Rate 1 -Duration 3m
```

Repeat the command separately with the next approved rate only after the prior run has ended and the team has reviewed Grafana. The run metadata and k6 summary record `targetEnvironment`, `productionOptIn`, `a1Profile`, `rate`, `duration`, and derived VUs; the approval token itself is not recorded.

For the separately approved deployment observation, use `-A1Profile deployment-experiment -Rate 50 -Duration 5m`. The load tool never deploys: a human performs the deployment while Grafana is observed. Then use `-A1Profile deployment-recovery -Rate 0 -Duration 5m` for the no-request recovery window.

| Manual window | `A1Profile` | Rate / duration |
| --- | --- | --- |
| Baseline and ordinary recovery | `observation` | `0 RPS / 3m`, no HTTP request |
| Step 1–10 | `step` | `1, 5, 10, 25, 50, 100, 150, 200, 250, 300 RPS / up to 3m` |
| Deployment experiment | `deployment-experiment` | exactly `50 RPS / 5m` |
| Deployment recovery | `deployment-recovery` | `0 RPS / 5m`, no HTTP request |

For A1 mixed only, `http_req_failed: rate<0.01` has `abortOnFail: true` with `delayAbortEval: 120s`. At the lowest permitted rate (1 RPS), this gives roughly 120 observations: one transient failure remains under 1%, while sustained failures trigger an automatic k6 abort instead of continuing to the duration limit. Local smoke/performance profiles retain their existing result-only thresholds.

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

Its manifest rotates published Festival IDs `1/5001/10001/15001`, Artist IDs `1/10000/20000/30000`, Host IDs `1/7/100/200`, and list pages `0/1/100/500`. This avoids a fixed detail-ID or page-0 cache bias. Override a value with `FESTIVAL_ID`, `ARTIST_ID`, `HOST_ID`, `FESTIVAL_PAGE`, or `ARTIST_PAGE` only for an explicitly fixed comparison condition.

`FIXTURE=a1` is intentionally separate: it loads the untracked A1 manifest through the wrapper and is accepted only for the A1 validation, observation, and mixed profiles above. This prevents a local synthetic fixture ID or search string from silently becoming an A1 request target.

`FIXTURE=smoke` uses the isolated local seed and IDs `900001`. The local seed includes the same canonical search strings as the performance fixture but is deliberately tiny.

## Search corpus

The 20-entry corpus advances by the ordinal of the search request, not its absolute iteration. A standalone `search` scenario uses its own iteration ordinal; `mixed` converts only its interleaved search slots to a search ordinal. This keeps both modes deterministic without a random source or changing seed.

| Bucket | Slots | Ratio | Examples | API `type` |
| --- | ---: | ---: | --- | --- |
| Common 1-character | 6 | 30% | `김`, `축`, `성` | `ALL`, `ARTIST`, `FESTIVAL`, `HOST` |
| Common 2-character | 6 | 30% | `김아`, `축제`, `성능` | `ALL`, `ARTIST`, `FESTIVAL`, `HOST` |
| Rare | 3 | 15% | `희귀 아티스트 30000`, `희귀 검색 축제 20000`, `PERF171` | single type |
| Space-normalized | 3 | 15% | `서울축제`, `성능대학교` | `ALL`, `FESTIVAL`, `HOST` |
| English | 2 | 10% | `campus`, `spring` | `ARTIST`, `FESTIVAL` |

Use `-SearchBucket rare` or `-SearchType ARTIST` through the wrapper to isolate a corpus subset. The full corpus must be used for mixed/final runs. Run `fixture-manifest` after seeding and before each compared series; it requires every manifest target and every corpus query/type to return a result.

For the #185 list comparison, preserve the historical fixed request exactly:

```powershell
.\run.ps1 -Runner docker -BaseUrl 'http://host.docker.internal:18080' -Stage baseline -Scenario festivals -Fixture performance -FestivalPage 0 -Rate 5 -Duration 3m -PreAllocatedVUs 2 -MaxVUs 2
```

While each warm-up or measured run is active, collect the existing container-level evidence into that same run directory. This adds no monitoring service and keeps raw metrics out of Git:

```powershell
.\collect-runtime-metrics.ps1 -ResultDirectory .\results\<run-id> -AppContainer <app-container> -PostgresContainer <postgres-container> -DurationSeconds 180 -IntervalSeconds 10
```

## Load stages

| `STAGE` | Executor | Starting point | Duration |
| --- | --- | ---: | ---: |
| `smoke` | 1 VU, shared iterations | 100 iterations | max 2 min |
| `baseline` | constant arrival rate | 5 RPS | 3 min |
| `normal` | constant arrival rate | 15 RPS | 5 min |
| `stress` | constant arrival rate | 30 RPS | 5 min |
| `saturation` | constant arrival rate | 50 RPS | 5 min |

The latter four values are conservative starting rates, not a fixed maximum TPS or an acceptance target. Set `RATE`, `DURATION`, `PRE_ALLOCATED_VUS`, and `MAX_VUS` only after the temporary VM's CPU/RAM/disk/network snapshot is recorded. Before every measured baseline/normal/stress/saturation stage, perform a separate warm-up run at the same profile for at least two minutes and discard its artifacts from the comparison. A final sustained maximum is the highest rate that meets the agreed latency/error conditions for at least three minutes; this script does not silently declare one.

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

On Linux Docker hosts the wrapper adds `host.docker.internal:host-gateway` and first verifies that the image's non-root k6 user can write the run directory. If that check fails, fix the directory ownership or use a writable dedicated results location; do not run a measurement without its summary artifact.

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

Every request has an `endpoint` tag; search additionally has `search_bucket` and `search_type`. The saved summary separates endpoint request `{ count, rps }`, latency p50/p95/p99, HTTP error rate, and response-check failure count. If any threshold fails, `failedThresholds` records the metric and expression in the JSON summary and its count is printed to stdout. k6 also exposes VU gauges.

k6 provides an exact `{endpoint:X}` submetric in addition to more-specific tag series. Endpoint summaries use that aggregate directly, preserving its count, RPS, average latency, and p50/p95/p99. Search bucket/type summaries remain separate leaf-series aggregates; a percentile is `null` there only if multiple series would need an inexact reconstruction.

The defined thresholds are deliberately only safety/contract guards:

- HTTP failures below 1%
- checks above 99%
- p99 below 10 seconds (a hung-request guard)

They are not final performance acceptance criteria. Final reports must show actual RPS/TPS, p50/p95/p99, errors, VUs/arrival rate, app/PostgreSQL CPU and memory, DB connections, and available JVM/GC/Hikari observations.

## Final temporary-VM run

Before final measurement:

1. Record temporary VM hardware, OS, Docker/container limits, JVM settings, PostgreSQL settings, disk/volume size, and k6 runner/network location.
2. Recreate the Issue #154 fixture in a separate PostgreSQL volume and use `FIXTURE=performance`. Its generator is deliberately not copied into this lightweight tool branch; use the reviewed source tracked with [Issue #154](https://github.com/greedy-team/festa-backend/issues/154), not the local smoke seed. After seeding, verify and record that every `SEARCH_CORPUS` query/type pair returns at least one result.
3. Build and measure the BEFORE image from baseline commit `b43c872` with this fixture. If #184/#185 have already merged by the time of measurement, reproduce it explicitly from that commit (for example with a detached worktree), rather than substituting a later image.
4. Merge PR #184 and PR #185, build the latest `develop` image, then repeat the same AFTER measurement. Keep fixture, image/container settings other than the compared changes, warm-up rule, stage, runner location, and network conditions fixed.
5. During every measured run, collect app/PostgreSQL CPU and memory, DB connection count, and available JVM/GC/Hikari observations at a fixed interval into that run's directory. Record the container names, SQL command, sampling interval, and runner/network location beside the k6 metadata.
6. For both BEFORE and AFTER, run smoke, then baseline → normal → stress → saturation. Warm up every measured baseline/normal/stress/saturation profile first, repeat stable stages, and retain result directories outside Git.

Do not treat this branch's local smoke as the final API-level proof of the #184/#185 optimizations.
