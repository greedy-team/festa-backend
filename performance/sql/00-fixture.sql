-- PERFORMANCE FIXTURE ONLY.  This is intentionally outside Flyway.
-- It is loaded only by performance/bootstrap.sh into database festa_perf.
-- Recreate the named performance volume to make this fixture deterministic.

\set ON_ERROR_STOP on
SET client_min_messages = warning;
SET synchronous_commit = off;

-- Preserve the 29 real host seed rows, then add enough hosts to make joins and
-- coverage scans meaningful.  The generated values are deliberately synthetic.
INSERT INTO host (name, short_name, region, created_at, updated_at)
SELECT
    CASE WHEN n % 2 = 0 THEN format('Performance University %s', n)
         ELSE format('성능 대학교 %s', n) END,
    format('PERF%s', n),
    'performance-fixture', now(), now()
FROM generate_series(1, 171) AS n;

INSERT INTO artist (name, genre, needs_review, created_at, updated_at)
SELECT
    CASE
        WHEN n <= 9000 THEN format('김 아티스트 %s', n)                 -- common one/two-char Korean search
        WHEN n <= 18000 THEN format('Campus Artist %s', n)              -- English search
        WHEN n <= 29999 THEN format('무공백아티스트%s', n)               -- no-space counterpart
        ELSE '희귀 아티스트 30000'                                      -- known rare query
    END,
    (ARRAY['DANCE', 'HIPHOP', 'BALLAD_RNB', 'BAND'])[1 + (n % 4)],
    n % 10 = 0, now(), now()
FROM generate_series(1, 30000) AS n;

INSERT INTO artist_alias (artist_id, name)
SELECT
    1 + ((n - 1) % 30000),
    CASE
        WHEN n <= 15000 THEN format('별칭 아티스트 %s', n)
        WHEN n <= 30000 THEN format('Alias Artist %s', n)
        ELSE format('별칭%s', n)
    END
FROM generate_series(1, 45000) AS n;

-- 80% published.  The date bands guarantee UPCOMING, ONGOING, and ENDED rows
-- relative to the date on which the fixture is loaded.
INSERT INTO festival (
    host_id, import_key, name, start_date, end_date, published_at, discovery,
    created_at, updated_at
)
SELECT
    1 + ((n - 1) % 200),
    format('PERF-%s', n),
    CASE
        WHEN n <= 6000 THEN format('서울 축제 %s', n)                    -- common Korean and space case
        WHEN n <= 12000 THEN format('Spring Campus Festival %s', n)     -- English case
        WHEN n <= 19999 THEN format('무공백축제%s', n)
        ELSE '희귀 검색 축제 20000'
    END,
    CASE
        WHEN n % 3 = 0 THEN CURRENT_DATE + (n % 180) + 1               -- upcoming
        WHEN n % 3 = 1 THEN CURRENT_DATE - (n % 3)                      -- ongoing
        ELSE CURRENT_DATE - (n % 365) - 2                              -- ended
    END,
    CASE
        WHEN n % 3 = 0 THEN CURRENT_DATE + (n % 180) + 3
        WHEN n % 3 = 1 THEN CURRENT_DATE + 2
        ELSE CURRENT_DATE - (n % 365) - 1
    END,
    CASE WHEN n % 5 = 0 AND n <> 20000 THEN NULL ELSE now() - ((n % 90) || ' days')::interval END,
    CASE WHEN n % 4 = 0 THEN 'MANUAL' ELSE 'CRAWLED' END,
    now(), now()
FROM generate_series(1, 20000) AS n;

-- Exactly 15 rows per festival: three days, five display orders per day.
INSERT INTO lineup (festival_id, artist_id, day, display_order)
SELECT
    1 + ((n - 1) / 15),
    CASE WHEN n % 20 = 0 THEN NULL ELSE 1 + ((n * 37) % 30000) END,
    1 + ((n - 1) % 3),
    1 + (((n - 1) % 15) / 3)
FROM generate_series(1, 300000) AS n;

ANALYZE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM host h
        WHERE lower(replace(h.short_name, ' ', '')) LIKE '%' || lower('PERF171') || '%'
    ) THEN
        RAISE EXCEPTION 'fixture rare host search must return a result';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM festival f
        JOIN host h ON h.id = f.host_id
        WHERE f.published_at IS NOT NULL
          AND h.id = 1
          AND f.start_date >= date_trunc('year', CURRENT_DATE)::date
          AND f.start_date < (date_trunc('year', CURRENT_DATE) + interval '1 year')::date
          AND f.start_date > CURRENT_DATE
          AND EXISTS (SELECT 1 FROM lineup l WHERE l.festival_id = f.id AND l.artist_id = 21038)
    ) THEN
        RAISE EXCEPTION 'fixture combined host/artist filter must return a result';
    END IF;
END $$;

SELECT 'fixture_counts' AS label,
       (SELECT count(*) FROM host) AS hosts,
       (SELECT count(*) FROM festival) AS festivals,
       (SELECT count(*) FROM artist) AS artists,
       (SELECT count(*) FROM artist_alias) AS artist_aliases,
       (SELECT count(*) FROM lineup) AS lineups;
