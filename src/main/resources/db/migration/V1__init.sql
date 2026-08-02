-- 검색용 PostgreSQL 확장
-- pg_trgm  : trigram 기반 유사도 검색 / LIKE 인덱싱
-- unaccent : 발음 구별 기호 제거(악센트 무시 검색)
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;
