CREATE INDEX idx_import_commit_row_batch_id
    ON import_commit_row (batch_id);

CREATE INDEX idx_import_batch_uploaded_at_id_desc
    ON import_batch (uploaded_at DESC, id DESC);
