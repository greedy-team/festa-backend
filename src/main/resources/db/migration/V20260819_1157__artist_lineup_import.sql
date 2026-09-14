-- 아티스트 도메인
CREATE TABLE artist (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          varchar     NOT NULL,
    genre         varchar,
    image_url     varchar,
    instagram_url varchar,
    needs_review  boolean     NOT NULL,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,

    CONSTRAINT uq_artist_name UNIQUE (name)
);

CREATE TABLE artist_alias (
    id        bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    artist_id bigint  NOT NULL,
    name      varchar NOT NULL,

    CONSTRAINT uq_artist_alias_name UNIQUE (name),
    CONSTRAINT fk_artist_alias_artist
        FOREIGN KEY (artist_id) REFERENCES artist (id)
);

CREATE TABLE lineup (
    id            bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    festival_id   bigint  NOT NULL,
    artist_id     bigint,
    day           integer NOT NULL,
    display_order integer NOT NULL,

    CONSTRAINT uq_lineup_festival_day_display_order
        UNIQUE (festival_id, day, display_order),
    CONSTRAINT fk_lineup_festival
        FOREIGN KEY (festival_id) REFERENCES festival (id),
    CONSTRAINT fk_lineup_artist
        FOREIGN KEY (artist_id) REFERENCES artist (id)
);

-- 관리자와 임포트 도메인
CREATE TABLE admin_user (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      varchar     NOT NULL,
    password_hash varchar     NOT NULL,
    created_at    timestamptz NOT NULL,

    CONSTRAINT uq_admin_user_username UNIQUE (username)
);

CREATE TABLE import_batch (
    id                   bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type                 varchar     NOT NULL,
    file_names           text[]      NOT NULL,
    on_conflict          varchar     NOT NULL,
    preview              text,
    uploaded_by_admin_id bigint,
    uploaded_at          timestamptz NOT NULL,
    expires_at           timestamptz NOT NULL,
    committed_at         timestamptz,

    CONSTRAINT fk_import_batch_uploaded_by_admin
        FOREIGN KEY (uploaded_by_admin_id) REFERENCES admin_user (id) ON DELETE SET NULL
);

CREATE TABLE import_commit_row (
    id           bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id     bigint      NOT NULL,
    section      varchar     NOT NULL,
    line         integer     NOT NULL,
    import_key   varchar     NOT NULL,
    action       varchar     NOT NULL,
    festival_id  bigint,
    artist_id    bigint,
    payload      jsonb       NOT NULL,
    committed_at timestamptz NOT NULL,

    CONSTRAINT fk_import_commit_row_batch
        FOREIGN KEY (batch_id) REFERENCES import_batch (id),
    CONSTRAINT fk_import_commit_row_festival
        FOREIGN KEY (festival_id) REFERENCES festival (id) ON DELETE SET NULL,
    CONSTRAINT fk_import_commit_row_artist
        FOREIGN KEY (artist_id) REFERENCES artist (id) ON DELETE SET NULL
);
