-- =====================================================================
-- The Blast Beast — agenda de conciertos
-- PostgreSQL 16+
-- =====================================================================

-- ---------------------------------------------------------------------
-- Salas y recintos
-- ---------------------------------------------------------------------
create table venues (
    id           bigint generated always as identity primary key,
    source       varchar(20)  not null default 'TICKETMASTER'
                 check (source in ('TICKETMASTER', 'DEMO')),
    external_id  varchar(100) not null,
    name         varchar(200) not null,
    city         varchar(120) not null,
    city_slug    varchar(120) not null,
    region       varchar(120),
    country_code varchar(2)   not null default 'ES',
    address      varchar(300),
    latitude     numeric(9, 6),
    longitude    numeric(9, 6),
    timezone     varchar(60),
    created_at   timestamptz  not null default now(),
    updated_at   timestamptz  not null default now(),
    constraint uq_venues_source_external unique (source, external_id)
);

create index idx_venues_city on venues (city_slug);

comment on table venues is 'Salas y recintos de la agenda, normalizados por fuente';

-- ---------------------------------------------------------------------
-- Conciertos
-- ---------------------------------------------------------------------
create table concerts (
    id                bigint generated always as identity primary key,
    source            varchar(20)  not null default 'TICKETMASTER'
                      check (source in ('TICKETMASTER', 'DEMO')),
    external_id       varchar(100) not null,
    slug              varchar(220) not null unique,
    title             varchar(300) not null,
    description       text,
    start_at          timestamptz  not null,
    end_at            timestamptz,
    time_tba          boolean      not null default false,
    status            varchar(20)  not null default 'ON_SALE'
                      check (status in ('ON_SALE', 'OFF_SALE', 'POSTPONED',
                                        'RESCHEDULED', 'CANCELLED', 'ARCHIVED')),
    genre             varchar(80),
    genre_slug        varchar(80),
    subgenre          varchar(80),
    ticket_url        varchar(600),
    source_url        varchar(600),
    price_min         numeric(10, 2),
    price_max         numeric(10, 2),
    currency          varchar(3),
    image_url         varchar(600),
    image_attribution varchar(200),
    venue_id          bigint       not null references venues (id),
    featured          boolean      not null default false,
    hidden            boolean      not null default false,
    last_seen_at      timestamptz  not null default now(),
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    constraint uq_concerts_source_external unique (source, external_id)
);

comment on table concerts is 'Agenda de conciertos importada de Ticketmaster y curada por la redacción';
comment on column concerts.last_seen_at is 'Última sincronización en la que la fuente devolvió el concierto';

create index idx_concerts_agenda on concerts (hidden, status, start_at);
create index idx_concerts_start on concerts (start_at);
create index idx_concerts_genre on concerts (genre_slug);
create index idx_concerts_venue on concerts (venue_id);

-- ---------------------------------------------------------------------
-- Cartel
-- ---------------------------------------------------------------------
create table concert_artists (
    id          bigint generated always as identity primary key,
    concert_id  bigint       not null references concerts (id) on delete cascade,
    external_id varchar(100),
    name        varchar(200) not null,
    slug        varchar(200) not null,
    band_id     bigint       references bands (id) on delete set null,
    headliner   boolean      not null default false,
    sort_order  int          not null default 0,
    constraint uq_concert_artists_slug unique (concert_id, slug)
);

create index idx_concert_artists_slug on concert_artists (slug);
create index idx_concert_artists_band on concert_artists (band_id);

-- ---------------------------------------------------------------------
-- Historial de sincronizaciones
-- ---------------------------------------------------------------------
create table concert_sync_runs (
    id            bigint generated always as identity primary key,
    started_at    timestamptz  not null default now(),
    finished_at   timestamptz,
    status        varchar(20)  not null default 'RUNNING'
                  check (status in ('RUNNING', 'SUCCESS', 'FAILED')),
    fetched       int          not null default 0,
    created       int          not null default 0,
    updated       int          not null default 0,
    archived      int          not null default 0,
    error_message varchar(500)
);

create index idx_concert_sync_runs_started on concert_sync_runs (started_at desc);

-- ---------------------------------------------------------------------
-- updated_at automático
-- ---------------------------------------------------------------------
create trigger trg_venues_updated_at
    before update on venues
    for each row execute function set_updated_at();

create trigger trg_concerts_updated_at
    before update on concerts
    for each row execute function set_updated_at();
