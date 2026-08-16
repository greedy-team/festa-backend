-- 축제 도메인 — 주최 · 축제 · 축제 해시태그
-- 아티스트 · 라인업 · 집계 뷰는 V3에서 추가한다.

CREATE TABLE host (
    id            bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          varchar     NOT NULL,
    short_name    varchar,
    region        varchar     NOT NULL,
    banner_url    varchar,
    homepage_url  varchar,
    instagram_url varchar,
    created_at    timestamptz NOT NULL,
    updated_at    timestamptz NOT NULL,

    CONSTRAINT uq_host_name UNIQUE (name)
);

-- 임포트는 주최를 자동 생성하지 않는다.
-- name이 CSV 매칭 키이며 시드로 선등록한다 — 연세대와 연세대학교가 따로 생기는 것을 막는다.


CREATE TABLE festival (
    id               bigint           GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    host_id          bigint,
    import_key       varchar,

    name             varchar          NOT NULL,
    start_date       date             NOT NULL,
    end_date         date             NOT NULL,
    poster_url       varchar,
    description      text,

    venue_name       varchar,
    address          varchar,
    latitude         double precision,
    longitude        double precision,

    external_visitor varchar,
    verification     varchar,
    ticket_type      varchar,
    ticket_open_at   timestamptz,
    admission_note   varchar,
    admission_raw    varchar,

    instagram_url    varchar,
    published_at     timestamptz,

    discovery        varchar,
    crawl_flag       varchar,
    source_url       varchar,
    imported_at      timestamptz,

    created_at       timestamptz      NOT NULL,
    updated_at       timestamptz      NOT NULL,

    CONSTRAINT uq_festival_import_key UNIQUE (import_key),
    CONSTRAINT fk_festival_host FOREIGN KEY (host_id) REFERENCES host (id)
);

-- NOT NULL이 셋뿐인 것은 의도다. 미완성 저장을 허용하고 발행 게이트가 막는다.
-- host_id·좌표가 비어도 저장되며 발행에서 걸러진다.
-- import_key는 UNIQUE지만 nullable — 어드민 직접 등록은 값이 없고,
-- Postgres는 NULL을 서로 다른 값으로 보므로 여러 행이 NULL이어도 UNIQUE가 안 깨진다.


CREATE TABLE festival_hashtag (
    id          bigint  GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    festival_id bigint  NOT NULL,
    tag         varchar NOT NULL,

    CONSTRAINT uq_festival_hashtag UNIQUE (festival_id, tag),
    CONSTRAINT fk_festival_hashtag_festival
        FOREIGN KEY (festival_id) REFERENCES festival (id) ON DELETE CASCADE
);

-- 태그는 '#' 없이 저장한다. 순서는 두지 않는다 — 삽입 순서로 충분하다.