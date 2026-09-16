-- ============================================================
--  V3 — DOM8 Reference Data (airports, aircraft types)
--  tenant_id is nullable: a row with NULL is the common
--  reference set, a row with a tenant is that tenant's override.
-- ============================================================
CREATE TABLE refdata.aircraft_types (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'refdata',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    icao_type         text        NOT NULL,
    manufacturer      text,
    model             text        NOT NULL,
    wake_category     text,
    mtow_kg           integer,
    min_runway_ft     integer,
    max_pax           integer,
    range_nm          integer,
    cruise_tas_kt     integer,
    etops_applicable  boolean     NOT NULL DEFAULT false,
    CONSTRAINT uq_aircraft_types_icao UNIQUE (icao_type)
);

CREATE TABLE refdata.airports (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    source_type        text        NOT NULL DEFAULT 'refdata',
    source_ref         text,
    source_version     text,
    source_author      uuid,
    source_at          timestamptz NOT NULL DEFAULT now(),
    icao               text        NOT NULL,
    iata               text,
    name               text        NOT NULL,
    city               text,
    country_iso2       text        NOT NULL,
    latitude           numeric(9,6),
    longitude          numeric(9,6),
    elevation_ft       integer,
    longest_runway_ft  integer,
    aerodrome_category text,
    rffs_category      text,
    time_zone          text,
    CONSTRAINT uq_airports_icao UNIQUE (icao),
    CONSTRAINT ck_airports_category CHECK (aerodrome_category IS NULL OR aerodrome_category IN ('A', 'B', 'C'))
);

CREATE INDEX ix_airports_iata ON refdata.airports (iata);
CREATE INDEX ix_airports_country ON refdata.airports (country_iso2);
