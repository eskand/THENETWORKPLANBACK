-- ============================================================
--  V17 — Transverse : paramètres de l'exploitant, jeux de
--  référence administrables, rapports enregistrés, scénarios
--  de simulation.
--
--  Constats d'audit corrigés :
--    « Simulation Center : stub aléatoire (l. 90776) écrit sur
--      la Timeline live (l. 92463) »  -> les scénarios vivent dans
--                                        leur propre schéma et
--                                        n'écrivent JAMAIS dans ops
--    « des réglages existaient dans l'UI et n'étaient lus nulle
--      part »                          -> platform.settings, une clé,
--                                        une valeur, un lecteur
-- ============================================================

-- ------------------------------------------------------------
--  1. Paramètres de l'exploitant.
--     Une clé n'existe que si quelqu'un la lit : la colonne
--     read_by nomme le service qui la consomme.
-- ------------------------------------------------------------
CREATE TABLE platform.settings (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    category        text        NOT NULL,
    setting_key     text        NOT NULL,
    setting_value   text        NOT NULL,
    value_type      text        NOT NULL DEFAULT 'STRING',
    unit            text,
    label           text        NOT NULL,
    description     text,
    read_by         text        NOT NULL,
    editable        boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_setting UNIQUE (tenant_id, setting_key),
    CONSTRAINT ck_setting_type CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DURATION', 'ENUM')),
    CONSTRAINT ck_setting_category CHECK (category IN (
        'OPS', 'CREW', 'MAINTENANCE', 'TRIP_SUPPORT', 'SAFETY', 'COMMERCIAL', 'PLATFORM'))
);

CREATE INDEX ix_settings_category ON platform.settings (tenant_id, category);

COMMENT ON COLUMN platform.settings.read_by IS
    'Le service qui lit cette cle. Un reglage que personne ne lit est un mensonge d ecran (constat d audit).';

-- ------------------------------------------------------------
--  2. Rapports enregistrés : la définition, pas le résultat.
--     Un rapport est une question posée à la base ; son résultat
--     est recalculé à chaque exécution et daté.
-- ------------------------------------------------------------
CREATE TABLE platform.report_definitions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    code            text        NOT NULL,
    title           text        NOT NULL,
    domain          text        NOT NULL,
    description     text,
    default_window_days integer NOT NULL DEFAULT 30,
    CONSTRAINT uq_report_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_report_domain CHECK (domain IN (
        'OPS', 'CREW', 'MAINTENANCE', 'TRIP_SUPPORT', 'SAFETY', 'COMMERCIAL'))
);

CREATE TABLE platform.report_runs (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'engine',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    definition_id   uuid        NOT NULL REFERENCES platform.report_definitions (id) ON DELETE CASCADE,
    ran_at          timestamptz NOT NULL DEFAULT now(),
    ran_by          uuid        REFERENCES platform.users (id),
    window_from     date        NOT NULL,
    window_to       date        NOT NULL,
    row_count       integer     NOT NULL DEFAULT 0,
    duration_ms     integer,
    CONSTRAINT ck_report_run_window CHECK (window_to >= window_from)
);

CREATE INDEX ix_report_runs_definition ON platform.report_runs (definition_id, ran_at DESC);

-- ------------------------------------------------------------
--  3. Simulation : scénarios et leurs événements.
--     Schéma séparé, aucune clé étrangère vers ops : un scénario
--     ne peut pas, par construction, écrire dans le programme réel.
-- ------------------------------------------------------------
CREATE TABLE planning.simulation_scenarios (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    code            text        NOT NULL,
    title           text        NOT NULL,
    kind            text        NOT NULL,
    narrative       text,
    baseline_date   date        NOT NULL,
    status          text        NOT NULL DEFAULT 'DRAFT',
    last_run_at     timestamptz,
    last_run_by     uuid        REFERENCES platform.users (id),
    CONSTRAINT uq_scenario_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_scenario_kind CHECK (kind IN ('AOG', 'WEATHER', 'CREW_SHORTAGE', 'AIRSPACE', 'STRIKE', 'MEDICAL', 'OTHER')),
    CONSTRAINT ck_scenario_status CHECK (status IN ('DRAFT', 'READY', 'RUN', 'ARCHIVED'))
);

COMMENT ON TABLE planning.simulation_scenarios IS
    'Bac a sable. Aucune cle etrangere vers ops : un scenario ne peut pas ecrire dans le programme reel (constat d audit sur le Simulation Center).';

CREATE TABLE planning.simulation_events (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    scenario_id     uuid        NOT NULL REFERENCES planning.simulation_scenarios (id) ON DELETE CASCADE,
    sequence_no     integer     NOT NULL,
    offset_minutes  integer     NOT NULL DEFAULT 0,
    kind            text        NOT NULL,
    -- Références par valeur, jamais par clé étrangère : le scénario
    -- désigne une immatriculation ou un numéro de vol par son texte.
    registration    text,
    flight_no       text,
    station_icao    text,
    detail          text        NOT NULL,
    expected_action text,
    CONSTRAINT uq_simulation_event UNIQUE (scenario_id, sequence_no),
    CONSTRAINT ck_simulation_event_kind CHECK (kind IN (
        'AOG_DECLARED', 'DELAY', 'DIVERSION', 'CREW_UNAVAILABLE', 'AIRPORT_CLOSED',
        'PERMIT_REFUSED', 'MEDICAL', 'INJECT', 'DECISION_POINT'))
);

CREATE INDEX ix_simulation_events_scenario ON planning.simulation_events (scenario_id, sequence_no);
