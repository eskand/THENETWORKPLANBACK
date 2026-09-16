-- ============================================================
--  V13 — Opérations : suivi de vol, fournisseurs, annuaire
--  d'aérodromes (pistes et notes).
--
--  Constats d'audit corrigés :
--    « Flight Following : simulation + anomalies aléatoires
--      auto-démarrées, icao24 fabriqués »   -> ops.position_reports
--                                              porte sa source, et rien
--                                              n'est produit sans source
--    « Airports : deux annuaires concurrents (l. 6795 vs 61399) »
--                                           -> refdata.runways et
--                                              refdata.airport_notes
--                                              rattachés au SEUL
--                                              refdata.airports
-- ============================================================

-- ------------------------------------------------------------
--  1. Positions. Une ligne = une position reçue, avec sa source
--     et l'instant de réception. Aucune interpolation n'est
--     stockée : ce qui n'a pas été reçu n'existe pas.
-- ------------------------------------------------------------
CREATE TABLE ops.position_reports (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'integration',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    leg_id            uuid        REFERENCES ops.legs (id),
    aircraft_id       uuid        NOT NULL REFERENCES camo.aircraft (id),
    reported_at       timestamptz NOT NULL,
    received_at       timestamptz NOT NULL DEFAULT now(),
    latitude          numeric(9,6) NOT NULL,
    longitude         numeric(9,6) NOT NULL,
    altitude_ft       integer,
    ground_speed_kt   integer,
    track_deg         integer,
    vertical_rate_fpm integer,
    on_ground         boolean,
    provider          text        NOT NULL,
    provider_ref      text,
    CONSTRAINT ck_position_provider CHECK (provider IN ('ADSB', 'ACARS', 'SATCOM', 'MANUAL', 'RADAR')),
    CONSTRAINT ck_position_lat CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_position_lon CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT ck_position_track CHECK (track_deg IS NULL OR track_deg BETWEEN 0 AND 360)
);

CREATE INDEX ix_positions_leg_time ON ops.position_reports (leg_id, reported_at DESC);
CREATE INDEX ix_positions_aircraft_time ON ops.position_reports (aircraft_id, reported_at DESC);

COMMENT ON TABLE ops.position_reports IS
    'Positions recues. provider dit d ou elle vient; une etape sans ligne est affichee NO POSITION SOURCE, jamais simulee.';

-- ------------------------------------------------------------
--  2. Fournisseurs par escale : le guichet de NetPlus Services.
-- ------------------------------------------------------------
CREATE TABLE tripsupport.suppliers (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    station_icao     text        NOT NULL,
    service_type     text        NOT NULL,
    name             text        NOT NULL,
    email            text,
    phone            text,
    sita             text,
    frequency        text,
    contract_ref     text,
    preferred        boolean     NOT NULL DEFAULT false,
    lead_time_hours  integer,
    active           boolean     NOT NULL DEFAULT true,
    remark           text,
    CONSTRAINT uq_supplier UNIQUE (tenant_id, station_icao, service_type, name),
    CONSTRAINT ck_supplier_service CHECK (service_type IN (
        'HANDLING', 'FUEL', 'CATERING', 'CREW_TRANSPORT', 'PAX_TRANSPORT',
        'CUSTOMS', 'DEICING', 'GAR', 'APIS'))
);

CREATE INDEX ix_suppliers_station ON tripsupport.suppliers (tenant_id, station_icao, service_type);

COMMENT ON COLUMN tripsupport.suppliers.preferred IS
    'Fournisseur retenu par defaut pour cette escale et ce service. Un seul devrait l etre; l ecran signale les doublons.';

-- ------------------------------------------------------------
--  3. Pistes : la longueur publiée, contre laquelle la readiness
--     compare la longueur minimale du type.
-- ------------------------------------------------------------
CREATE TABLE refdata.runways (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'refdata',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    airport_id       uuid        NOT NULL REFERENCES refdata.airports (id) ON DELETE CASCADE,
    designator       text        NOT NULL,
    length_ft        integer     NOT NULL,
    width_ft         integer,
    surface          text,
    lda_ft           integer,
    toda_ft          integer,
    ils_category     text,
    lighting         text,
    CONSTRAINT uq_runway UNIQUE (airport_id, designator),
    CONSTRAINT ck_runway_length CHECK (length_ft > 0),
    CONSTRAINT ck_runway_ils CHECK (ils_category IS NULL OR ils_category IN ('NONE', 'CAT_I', 'CAT_II', 'CAT_IIIA', 'CAT_IIIB'))
);

CREATE INDEX ix_runways_airport ON refdata.runways (airport_id);

-- ------------------------------------------------------------
--  4. Notes d'aérodrome : PPR, couvre-feu, créneaux, douane.
--     Une note a une fenêtre de validité et une provenance —
--     c'est ce qui manquait aux deux annuaires du prototype.
-- ------------------------------------------------------------
CREATE TABLE refdata.airport_notes (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'refdata',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    airport_id       uuid        NOT NULL REFERENCES refdata.airports (id) ON DELETE CASCADE,
    kind             text        NOT NULL,
    title            text        NOT NULL,
    detail           text,
    valid_from       date,
    valid_to         date,
    severity         text        NOT NULL DEFAULT 'INFO',
    CONSTRAINT ck_airport_note_kind CHECK (kind IN (
        'PPR', 'CURFEW', 'SLOT', 'CUSTOMS', 'FUEL', 'HANDLING', 'RESTRICTION', 'NOTE')),
    CONSTRAINT ck_airport_note_severity CHECK (severity IN ('INFO', 'ATTENTION', 'CRITICAL')),
    CONSTRAINT ck_airport_note_window CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_airport_notes_airport ON refdata.airport_notes (airport_id, kind);
