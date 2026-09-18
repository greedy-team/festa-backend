# Isolated PostgreSQL performance baseline

This directory is not a Flyway location and is not mounted by the application.
It creates only `festa_perf` on `127.0.0.1:55432`, using the separately named
`festa-perf-postgres-data` volume.  It never reads or writes the deployment
database volume.

For #192, initialize the named volume from the preserved `9ace799` BEFORE worktree
(#184 included, #185 excluded), then keep that volume unchanged while replacing only
the application image for AFTER. Do not run `down -v` between the two series.

## Run

```powershell
git worktree add ../festa-backend-perf-baseline 9ace799
$baselineMigrations = Join-Path (Resolve-Path ../festa-backend-perf-baseline) 'src/main/resources/db/migration'
cd performance
$env:PERF_MIGRATIONS_DIR = $baselineMigrations
docker compose up -d --wait
.\run-baseline.ps1
```

The first `up` applies that worktree's migration files to the isolated database,
then runs `sql/00-fixture.sql`. The fixture intentionally preserves the real host
seed and adds synthetic data only in `festa_perf`.

## Connect the application to the fixture

`bootstrap.sh` intentionally applies the baseline migration files with `psql`, so it
does not create `flyway_schema_history`. Start the application that serves the API
series with Flyway disabled and Hibernate validation left enabled. For an application
running on the host, use the loopback JDBC URL; for an application container, replace
`127.0.0.1` with the Docker-reachable PostgreSQL host.

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55432/festa_perf'
$env:DB_USERNAME = 'festa_perf'
$env:DB_PASSWORD = 'festa-perf-local-only'
$env:SPRING_FLYWAY_ENABLED = 'false'
$env:SPRING_JPA_HIBERNATE_DDL_AUTO = 'validate'
```

Use the same environment values for the BEFORE and AFTER application images. Do not
let an application with Flyway enabled attach to this pre-initialized volume: it has
no Flyway history by design.

To recreate the deterministic fixture, remove only this environment's volume:

```powershell
docker compose down -v
docker compose up -d --wait
```

`run-baseline.ps1` performs two warm-up passes, then five measured passes of
`sql/01-baseline.sql`, saving raw `EXPLAIN (ANALYZE, BUFFERS, SETTINGS)` output
under `results/`.  It does not create any index.

The fixture deliberately uses `CURRENT_DATE` for status bands.  This preserves
UPCOMING/ONGOING/ENDED coverage on every reproducible rebuild; record the run
date with results.

Before a measured API series, run `performance/load/run.ps1` with
`-Scenario fixture-manifest -Fixture performance` against the isolated app.
It verifies the stable detail-ID/page manifest and every search corpus
query/type, then the normal workload rotates those values deterministically.
