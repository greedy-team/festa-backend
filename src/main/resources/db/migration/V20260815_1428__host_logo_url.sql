-- 주최 로고 URL 추가
-- 배너와 별개다. 로고는 목록에서, 배너는 상세에서 쓴다.

ALTER TABLE host ADD COLUMN logo_url varchar;
