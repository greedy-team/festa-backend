CREATE INDEX idx_festival_host_start_id_unpublished
    ON festival (host_id, start_date, id)
    WHERE published_at IS NULL;

CREATE INDEX idx_festival_host_start_id_published
    ON festival (host_id, start_date, id)
    INCLUDE (end_date)
    WHERE published_at IS NOT NULL;

CREATE INDEX idx_lineup_artist_id
    ON lineup (artist_id);
