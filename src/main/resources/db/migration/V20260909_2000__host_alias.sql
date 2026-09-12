CREATE TABLE host_alias (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_id bigint NOT NULL,
    name varchar NOT NULL,
    CONSTRAINT fk_host_alias_host FOREIGN KEY (host_id) REFERENCES host (id) ON DELETE CASCADE,
    CONSTRAINT uq_host_alias_host_name UNIQUE (host_id, name)
);
