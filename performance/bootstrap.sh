#!/bin/sh
set -eu

# This runs only while the performance volume is first initialized.  It applies
# the application's migrations to festa_perf, then the non-Flyway fixture.
# No file below is in src/main/resources/db/migration.
for migration in $(find /migrations -maxdepth 1 -name '*.sql' -print | sort -V); do
  echo "Applying schema: ${migration}"
  psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    --set ON_ERROR_STOP=on --file "$migration"
done

psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set ON_ERROR_STOP=on --file /perf/00-fixture.sql
