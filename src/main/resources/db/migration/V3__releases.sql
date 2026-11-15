-- =====================================================================
-- The Blast Beast — radar de lanzamientos
-- PostgreSQL 16+
-- =====================================================================

create table releases (
    id           bigint generated always as identity primary key,
    slug         varchar(220) not null unique,
    title        varchar(200) not null,
    artist_name  varchar(160) not null,
    band_id      bigint       references bands (id) on delete set null,
    type         varchar(20)  not null
                 check (type in ('ALBUM', 'EP', 'SINGLE', 'LIVE', 'COMPILATION')),
    release_date date         not null,
    genre        varchar(80),
    genre_slug   varchar(80),
    label        varchar(120),
    listen_url   varchar(600),
    notes        varchar(500),
    featured     boolean      not null default false,
    hidden       boolean      not null default false,
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now()
);

comment on table releases is 'Calendario editorial de discos, EPs y singles';
comment on column releases.artist_name is 'Nombre del artista aunque no tenga ficha de banda';
comment on column releases.genre_slug is 'Género normalizado para filtros del radar';

create index idx_releases_date on releases (hidden, release_date);
create index idx_releases_genre on releases (genre_slug);
create index idx_releases_band on releases (band_id);

create trigger trg_releases_updated_at
    before update on releases
    for each row execute function set_updated_at();
