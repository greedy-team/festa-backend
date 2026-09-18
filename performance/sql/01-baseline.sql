-- BASELINE ONLY: do not add indexes in this file.
\set ON_ERROR_STOP on
SET client_min_messages = warning;
SET track_io_timing = on;

-- A. Integrated search: five normalized LIKE '%q%' cases per domain.
PREPARE artist_search(text) AS
SELECT a.id, COUNT(DISTINCT f.id) AS appearance_count, MAX(f.end_date) AS latest_appearance_date
FROM artist a
LEFT JOIN lineup l ON l.artist_id = a.id
LEFT JOIN festival f ON f.id = l.festival_id AND f.published_at IS NOT NULL AND f.end_date < CURRENT_DATE
WHERE lower(replace(a.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
   OR EXISTS (SELECT 1 FROM artist_alias al WHERE al.artist_id = a.id
              AND lower(replace(al.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\')
GROUP BY a.id
ORDER BY a.id ASC;
PREPARE artist_search_count(text) AS
SELECT COUNT(*) FROM artist a
WHERE lower(replace(a.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
   OR EXISTS (SELECT 1 FROM artist_alias al WHERE al.artist_id = a.id
              AND lower(replace(al.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\');

PREPARE host_search(text) AS
SELECT h.id, COUNT(DISTINCT f.id) AS festival_count, MAX(f.start_date) AS latest_festival_date
FROM host h
LEFT JOIN festival f ON f.host_id = h.id AND f.published_at IS NOT NULL
WHERE lower(replace(h.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
   OR lower(replace(h.short_name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
GROUP BY h.id
ORDER BY h.id ASC;
PREPARE host_search_count(text) AS
SELECT COUNT(*) FROM host h
WHERE lower(replace(h.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
   OR lower(replace(h.short_name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\';

PREPARE festival_search(text) AS
SELECT f.id FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL
  AND (lower(replace(f.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
       OR lower(replace(h.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
       OR lower(replace(h.short_name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\')
ORDER BY f.id ASC;
PREPARE festival_search_count(text) AS
SELECT COUNT(*) FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL
  AND (lower(replace(f.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
       OR lower(replace(h.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
       OR lower(replace(h.short_name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\');

PREPARE public_festival_search(text) AS
SELECT f.id FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL
  AND lower(replace(f.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
ORDER BY f.start_date ASC, f.id ASC LIMIT 20;
PREPARE public_festival_search_count(text) AS
SELECT COUNT(*) FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL
  AND lower(replace(f.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\';

PREPARE public_artist_name_search(text) AS
SELECT a.id, count(DISTINCT f.id) FROM artist a LEFT JOIN lineup l ON l.artist_id = a.id
LEFT JOIN festival f ON f.id = l.festival_id AND f.published_at IS NOT NULL AND f.end_date < CURRENT_DATE
WHERE lower(replace(a.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
   OR EXISTS (SELECT 1 FROM artist_alias al WHERE al.artist_id = a.id
              AND lower(replace(al.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\')
GROUP BY a.id ORDER BY min(a.name), a.id LIMIT 20;
PREPARE public_artist_search_count(text) AS
SELECT COUNT(*) FROM artist a
WHERE lower(replace(a.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\'
   OR EXISTS (SELECT 1 FROM artist_alias al WHERE al.artist_id = a.id
              AND lower(replace(al.name, ' ', '')) LIKE '%' || $1 || '%' ESCAPE E'\\');

\echo A1 artist 1-char common
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search('김');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search_count('김');
\echo A2 artist 2-char common
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search('김아');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search_count('김아');
\echo A3 artist rare
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search('희귀아티스트30000');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search_count('희귀아티스트30000');
\echo A4 artist spaced
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search('별칭아티스트');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search_count('별칭아티스트');
\echo A5 artist English
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search('campus');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE artist_search_count('campus');

\echo A6 host 1-char common
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search('성');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search_count('성');
\echo A7 host 2-char common
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search('성능');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search_count('성능');
\echo A8 host rare
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search(lower('PERF171'));
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search_count(lower('PERF171'));
\echo A9 host spaced
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search('성능대학교');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search_count('성능대학교');
\echo A10 host English
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search('performance');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE host_search_count('performance');

\echo A11 festival 1-char common
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search('축');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search_count('축');
\echo A12 festival 2-char common
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search('축제');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search_count('축제');
\echo A13 festival rare
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search('희귀검색축제20000');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search_count('희귀검색축제20000');
\echo A14 festival spaced
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search('서울축제');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search_count('서울축제');
\echo A15 festival English
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search('spring');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE festival_search_count('spring');

-- B. Public festival list: default, text search, and a combined filter.
\echo B1 public festival default and count
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT f.id FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL ORDER BY f.start_date ASC, f.id ASC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(*) FROM festival f JOIN host h ON h.id = f.host_id WHERE f.published_at IS NOT NULL;
\echo B2 public festival spaced search and count
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE public_festival_search('서울축제');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE public_festival_search_count('서울축제');
\echo B3 public festival combined host/year/status/artist and count
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT f.id FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL AND h.id = 1
  AND f.start_date >= date_trunc('year', CURRENT_DATE)::date
  AND f.start_date < (date_trunc('year', CURRENT_DATE) + interval '1 year')::date
  AND f.start_date > CURRENT_DATE
  AND EXISTS (SELECT 1 FROM lineup l WHERE l.festival_id = f.id AND l.artist_id = 21038)
ORDER BY f.start_date ASC, f.id ASC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(*) FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL AND h.id = 1
  AND f.start_date >= date_trunc('year', CURRENT_DATE)::date
  AND f.start_date < (date_trunc('year', CURRENT_DATE) + interval '1 year')::date
  AND f.start_date > CURRENT_DATE
  AND EXISTS (SELECT 1 FROM lineup l WHERE l.festival_id = f.id AND l.artist_id = 21038);

-- C. Public artist list: name order, appearance order, and alias search.
\echo C1 public artist name order and count
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT a.id, count(DISTINCT f.id) FROM artist a LEFT JOIN lineup l ON l.artist_id = a.id
LEFT JOIN festival f ON f.id = l.festival_id AND f.published_at IS NOT NULL AND f.end_date < CURRENT_DATE
GROUP BY a.id ORDER BY min(a.name), a.id LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) SELECT count(*) FROM artist;
\echo C2 public artist appearance order
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT a.id, count(DISTINCT f.id) FROM artist a LEFT JOIN lineup l ON l.artist_id = a.id
LEFT JOIN festival f ON f.id = l.festival_id AND f.published_at IS NOT NULL AND f.end_date < CURRENT_DATE
GROUP BY a.id ORDER BY count(DISTINCT f.id) DESC, a.id ASC LIMIT 20;
\echo C3 public artist alias EXISTS search and count
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE public_artist_name_search('별칭아티스트');
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) EXECUTE public_artist_search_count('별칭아티스트');

-- D. Native host coverage query with two LATERAL LIMIT 1 lookups.
\echo D1 host coverage
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT h.id, review_festival.id, current_festival.id
FROM host h
LEFT JOIN LATERAL (
  SELECT f.id FROM festival f WHERE f.host_id = h.id
    AND f.start_date >= date_trunc('year', CURRENT_DATE)::date
    AND f.start_date < (date_trunc('year', CURRENT_DATE) + interval '1 year')::date
    AND f.published_at IS NULL ORDER BY f.start_date ASC, f.id ASC LIMIT 1
) review_festival ON TRUE
LEFT JOIN LATERAL (
  SELECT f.id FROM festival f WHERE f.host_id = h.id
    AND f.start_date >= date_trunc('year', CURRENT_DATE)::date
    AND f.start_date < (date_trunc('year', CURRENT_DATE) + interval '1 year')::date
    AND f.end_date >= CURRENT_DATE AND f.published_at IS NOT NULL
  ORDER BY f.start_date ASC, f.id ASC LIMIT 1
) current_festival ON TRUE;

-- E. Home queries.
\echo E1 published not ended
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT f.id FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL AND f.end_date >= CURRENT_DATE
ORDER BY f.start_date ASC, f.id ASC LIMIT 50;
\echo E2 recently published
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT f.id FROM festival f JOIN host h ON h.id = f.host_id
WHERE f.published_at IS NOT NULL ORDER BY f.published_at DESC, f.id DESC LIMIT 30;

-- F. Artist-lineup joins and artist_id count paths.
\echo F1 published lineup by artist
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT l.id FROM lineup l JOIN festival f ON f.id = l.festival_id JOIN host h ON h.id = f.host_id
WHERE l.artist_id = 38 AND f.published_at IS NOT NULL;
\echo F2 count all lineups by artist
EXPLAIN (ANALYZE, BUFFERS, SETTINGS) SELECT count(*) FROM lineup WHERE artist_id = 38;
\echo F3 count completed appearances by artist
EXPLAIN (ANALYZE, BUFFERS, SETTINGS)
SELECT count(*) FROM lineup l JOIN festival f ON f.id = l.festival_id
WHERE l.artist_id = 38 AND f.published_at IS NOT NULL AND f.end_date < CURRENT_DATE;
