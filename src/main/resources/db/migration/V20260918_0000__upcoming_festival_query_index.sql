CREATE INDEX idx_festival_published_start_date_id
    ON festival (start_date ASC, id ASC)
    WHERE published_at IS NOT NULL;
