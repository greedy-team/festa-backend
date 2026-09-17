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
cd performance
$env:PERF_MIGRATIONS_DIR = 'C:\Users\Haeun\festa-brain\festa-backend\festa-backend-perf-baseline\src\main\resources\db\migration'
docker compose up -d --wait
.\run-baseline.ps1
```

The first `up` applies that worktree's migration files to the isolated database,
then runs `sql/00-fixture.sql`. The fixture intentionally preserves the real host
seed and adds synthetic data only in `festa_perf`.

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
