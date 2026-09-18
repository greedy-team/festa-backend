CREATE INDEX idx_artist_alias_artist_id
    ON artist_alias (artist_id);

CREATE INDEX idx_festival_host_id
    ON festival (host_id);

CREATE INDEX idx_festival_published_at_id_desc
    ON festival (published_at DESC, id DESC)
    WHERE published_at IS NOT NULL;
