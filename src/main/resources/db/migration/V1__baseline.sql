-- =====================================================================
-- The Blast Beast — esquema inicial
-- PostgreSQL 16+
-- =====================================================================

create extension if not exists pg_trgm;

-- ---------------------------------------------------------------------
-- Redacción
-- ---------------------------------------------------------------------
create table users (
    id            bigint generated always as identity primary key,
    email         varchar(254) not null unique,
    password_hash varchar(100) not null,
    display_name  varchar(120) not null,
    role          varchar(20)  not null default 'EDITOR'
                  check (role in ('ADMIN', 'EDITOR')),
    status        varchar(20)  not null default 'ACTIVE'
                  check (status in ('ACTIVE', 'DISABLED')),
    created_at    timestamptz  not null default now(),
    updated_at    timestamptz  not null default now()
);

comment on table users is 'Usuarios de la redacción (no lectores)';

create table refresh_tokens (
    id         bigint generated always as identity primary key,
    user_id    bigint      not null references users (id) on delete cascade,
    token_hash varchar(64) not null unique,
    expires_at timestamptz not null,
    revoked    boolean     not null default false,
    created_at timestamptz not null default now()
);

create index idx_refresh_tokens_user on refresh_tokens (user_id);

-- ---------------------------------------------------------------------
-- Taxonomía
-- ---------------------------------------------------------------------
create table categories (
    id          bigint generated always as identity primary key,
    slug        varchar(80)  not null unique,
    name        varchar(80)  not null,
    description varchar(300),
    sort_order  int          not null default 0,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);

create table bands (
    id           bigint generated always as identity primary key,
    slug         varchar(120) not null unique,
    name         varchar(120) not null,
    country      varchar(80),
    formed_year  int check (formed_year between 1900 and 2100),
    genre        varchar(60),
    website      varchar(300),
    bandcamp_url varchar(300),
    spotify_url  varchar(300),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);

create index idx_bands_name_trgm on bands using gin (name gin_trgm_ops);

create table tags (
    id   bigint generated always as identity primary key,
    slug varchar(80) not null unique,
    name varchar(80) not null
);

-- ---------------------------------------------------------------------
-- Medios
-- ---------------------------------------------------------------------
create table media_assets (
    id          bigint generated always as identity primary key,
    media_id    bigint references media_assets (id) on delete cascade,
    kind        varchar(20)  not null check (kind in ('ORIGINAL', 'THUMB', 'HERO')),
    storage_key varchar(300) not null unique,
    mime_type   varchar(100) not null,
    width       int check (width > 0),
    height      int check (height > 0),
    bytes       bigint       not null check (bytes > 0),
    alt_text    varchar(300),
    credit      varchar(200),
    created_at  timestamptz  not null default now()
);

comment on column media_assets.media_id is 'Variante de la que cuelga (null si es ORIGINAL)';

create index idx_media_assets_parent on media_assets (media_id);

-- ---------------------------------------------------------------------
-- Artículos
-- ---------------------------------------------------------------------
create table articles (
    id              bigint generated always as identity primary key,
    slug            varchar(160) not null unique,
    title           varchar(200) not null,
    excerpt         varchar(500) not null,
    body_md         text         not null,
    body_html       text         not null,
    category_id     bigint       not null references categories (id),
    author_id       bigint       not null references users (id),
    cover_media_id  bigint       references media_assets (id),
    status          varchar(20)  not null default 'DRAFT'
                    check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    published_at    timestamptz,
    reading_minutes int          not null default 0 check (reading_minutes >= 0),
    seo_title       varchar(200),
    seo_description varchar(320),
    canonical_url   varchar(500),
    search_vector   tsvector generated always as (
                        to_tsvector(
                            'spanish',
                            coalesce(title, '') || ' ' || coalesce(excerpt, '') || ' ' || coalesce(body_md, '')
                        )
                    ) stored,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    constraint chk_articles_published_consistency
        check (status <> 'PUBLISHED' or published_at is not null)
);

comment on table articles is 'Noticias del portal';
comment on column articles.body_md is 'Fuente en Markdown; es lo que edita la redacción';
comment on column articles.body_html is 'Markdown renderizado y saneado; es lo que se sirve al público';

create index idx_articles_status_published on articles (status, published_at desc nulls last);
create index idx_articles_category_published on articles (category_id, published_at desc nulls last);
create index idx_articles_author on articles (author_id);
create index idx_articles_search on articles using gin (search_vector);
create index idx_articles_title_trgm on articles using gin (title gin_trgm_ops);

create table article_bands (
    article_id bigint not null references articles (id) on delete cascade,
    band_id    bigint not null references bands (id) on delete cascade,
    primary key (article_id, band_id)
);

create index idx_article_bands_band on article_bands (band_id);

create table article_tags (
    article_id bigint not null references articles (id) on delete cascade,
    tag_id     bigint not null references tags (id) on delete cascade,
    primary key (article_id, tag_id)
);

create index idx_article_tags_tag on article_tags (tag_id);

create table article_revisions (
    id         bigint generated always as identity primary key,
    article_id bigint      not null references articles (id) on delete cascade,
    editor_id  bigint      not null references users (id),
    title      varchar(200) not null,
    body_md    text        not null,
    created_at timestamptz not null default now()
);

create index idx_article_revisions_article on article_revisions (article_id, created_at desc);

-- ---------------------------------------------------------------------
-- Newsletter
-- ---------------------------------------------------------------------
create table newsletter_subscribers (
    id                bigint generated always as identity primary key,
    email             varchar(254) not null unique,
    status            varchar(20)  not null default 'PENDING'
                      check (status in ('PENDING', 'CONFIRMED', 'UNSUBSCRIBED')),
    confirm_token     varchar(64)  unique,
    unsubscribe_token varchar(64)  unique,
    confirmed_at      timestamptz,
    created_at        timestamptz  not null default now()
);

-- ---------------------------------------------------------------------
-- updated_at automático
-- ---------------------------------------------------------------------
create or replace function set_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger trg_users_updated_at
    before update on users
    for each row execute function set_updated_at();

create trigger trg_categories_updated_at
    before update on categories
    for each row execute function set_updated_at();

create trigger trg_bands_updated_at
    before update on bands
    for each row execute function set_updated_at();

create trigger trg_articles_updated_at
    before update on articles
    for each row execute function set_updated_at();
