-- Local smoke only. The IDs and search strings are deterministic and intentionally
-- separate from the Issue #154 performance fixture/database.
\set ON_ERROR_STOP on

INSERT INTO host (id, name, short_name, region, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES
    (900001, '성능 대학교 1', 'PERF171', 'performance-fixture', now(), now()),
    (900002, 'Performance University 2', 'PERF172', 'performance-fixture', now(), now())
ON CONFLICT (id) DO UPDATE SET updated_at = EXCLUDED.updated_at;

INSERT INTO artist (id, name, genre, needs_review, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES
    (900001, '김 아티스트 1', 'HIPHOP', false, now(), now()),
    (900002, 'Campus Artist 1', 'BAND', false, now(), now()),
    (900003, '희귀 아티스트 30000', 'BALLAD_RNB', false, now(), now())
ON CONFLICT (id) DO UPDATE SET genre = EXCLUDED.genre, updated_at = EXCLUDED.updated_at;

INSERT INTO artist_alias (id, artist_id, name)
OVERRIDING SYSTEM VALUE
VALUES
    (900001, 900001, '별칭 아티스트 1'),
    (900002, 900002, 'Alias Artist 1')
ON CONFLICT (id) DO NOTHING;

INSERT INTO festival (id, host_id, import_key, name, start_date, end_date, published_at, discovery, created_at, updated_at)
OVERRIDING SYSTEM VALUE
VALUES
    (900001, 900001, 'LOAD-SMOKE-SEOUL', '서울 축제 1', CURRENT_DATE + 7, CURRENT_DATE + 9, now(), 'MANUAL', now(), now()),
    (900002, 900002, 'LOAD-SMOKE-SPRING', 'Spring Campus Festival 1', CURRENT_DATE + 10, CURRENT_DATE + 12, now(), 'MANUAL', now(), now()),
    (900003, 900001, 'LOAD-SMOKE-RARE', '희귀 검색 축제 20000', CURRENT_DATE - 30, CURRENT_DATE - 28, now(), 'MANUAL', now(), now())
ON CONFLICT (id) DO UPDATE SET
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    updated_at = EXCLUDED.updated_at,
    published_at = EXCLUDED.published_at;

INSERT INTO lineup (id, festival_id, artist_id, day, display_order)
OVERRIDING SYSTEM VALUE
VALUES
    (900001, 900001, 900001, 1, 1),
    (900002, 900002, 900002, 1, 1),
    (900003, 900003, 900003, 1, 1)
ON CONFLICT (id) DO NOTHING;

ANALYZE;
