-- ============================================================
--  V11 — DOM5 Airworthiness, suite : programme d'entretien,
--  échéancier, utilisation, consignes (AD/SB), carnet de bord
--  et défauts.
--
--  Constats d'audit corrigés (B_crew_camo) :
--    « deux vérités (camoFleet vs TNPCAMO) »        -> une seule table par fait
--    « TSN/CSN jamais alimentés »                   -> camo.utilisation, une ligne par vol
--    « CAMO fabriquée par hash (l. 14600) »         -> échéances calculées depuis le programme
--    « MEL dueDate '—' »                            -> camo.mel_library porte l'intervalle
-- ============================================================

-- ------------------------------------------------------------
--  1. Le programme d'entretien, par type d'aéronef.
--     C'est le modèle Part-M : une tâche, ses intervalles, sa
--     tolérance. Une tâche sans aucun intervalle est une tâche
--     à l'événement, et c'est une valeur admise.
-- ------------------------------------------------------------
CREATE TABLE camo.programme_tasks (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    source_type        text        NOT NULL DEFAULT 'manual',
    source_ref         text,
    source_version     text,
    source_author      uuid,
    source_at          timestamptz NOT NULL DEFAULT now(),
    aircraft_type_id   uuid        NOT NULL REFERENCES refdata.aircraft_types (id),
    code               text        NOT NULL,
    title              text        NOT NULL,
    ata_chapter        text,
    interval_hours     numeric(10,2),
    interval_cycles    integer,
    interval_months    integer,
    tolerance_hours    numeric(10,2),
    tolerance_days     integer,
    mandatory          boolean     NOT NULL DEFAULT true,
    reference          text,
    CONSTRAINT uq_programme_task UNIQUE (tenant_id, aircraft_type_id, code),
    CONSTRAINT ck_programme_interval CHECK (
        interval_hours IS NOT NULL OR interval_cycles IS NOT NULL OR interval_months IS NOT NULL
        OR mandatory = false)
);

CREATE INDEX ix_programme_tasks_type ON camo.programme_tasks (aircraft_type_id);

COMMENT ON TABLE camo.programme_tasks IS
    'Programme Part-M par type. Les echeances par immatriculation vivent dans camo.aircraft_tasks.';

-- ------------------------------------------------------------
--  2. L'échéancier, par immatriculation.
--     due_at_* est ÉCRIT quand la tâche est soldée (dernier
--     relevé + intervalle) et jamais recalculé à l'affichage.
-- ------------------------------------------------------------
CREATE TABLE camo.aircraft_tasks (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    source_type         text        NOT NULL DEFAULT 'manual',
    source_ref          text,
    source_version      text,
    source_author       uuid,
    source_at           timestamptz NOT NULL DEFAULT now(),
    aircraft_id         uuid        NOT NULL REFERENCES camo.aircraft (id),
    programme_task_id   uuid        REFERENCES camo.programme_tasks (id),
    code                text        NOT NULL,
    title               text        NOT NULL,
    last_done_on        date,
    last_done_hours     numeric(10,2),
    last_done_cycles    integer,
    due_on              date,
    due_at_hours        numeric(10,2),
    due_at_cycles       integer,
    closed_at           timestamptz,
    remark              text,
    CONSTRAINT uq_aircraft_task UNIQUE (aircraft_id, code)
);

CREATE INDEX ix_aircraft_tasks_due ON camo.aircraft_tasks (tenant_id, due_on) WHERE closed_at IS NULL;
CREATE INDEX ix_aircraft_tasks_aircraft ON camo.aircraft_tasks (aircraft_id);

-- ------------------------------------------------------------
--  3. L'utilisation, une ligne par vol effectué.
--     TSN et CSN de camo.aircraft sont la somme de cette table
--     plus le relevé initial : ils cessent d'être un hash.
-- ------------------------------------------------------------
CREATE TABLE camo.utilisation (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'techlog',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    aircraft_id     uuid        NOT NULL REFERENCES camo.aircraft (id),
    leg_id          uuid        REFERENCES ops.legs (id),
    flown_on        date        NOT NULL,
    block_minutes   integer     NOT NULL,
    air_minutes     integer,
    cycles          integer     NOT NULL DEFAULT 1,
    CONSTRAINT uq_utilisation_leg UNIQUE (leg_id),
    CONSTRAINT ck_utilisation_block CHECK (block_minutes >= 0),
    CONSTRAINT ck_utilisation_cycles CHECK (cycles >= 0)
);

CREATE INDEX ix_utilisation_aircraft_day ON camo.utilisation (aircraft_id, flown_on);

-- ------------------------------------------------------------
--  4. Consignes de navigabilité et bulletins de service.
--     La consigne est éditée par une autorité ; son application
--     est par immatriculation, d'où les deux tables.
-- ------------------------------------------------------------
CREATE TABLE camo.directives (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    source_type         text        NOT NULL DEFAULT 'manual',
    source_ref          text,
    source_version      text,
    source_author       uuid,
    source_at           timestamptz NOT NULL DEFAULT now(),
    kind                text        NOT NULL,
    reference           text        NOT NULL,
    subject             text        NOT NULL,
    issued_by           text,
    issued_on           date,
    effective_on        date,
    aircraft_type_id    uuid        REFERENCES refdata.aircraft_types (id),
    compliance_by_date  date,
    compliance_by_hours numeric(10,2),
    method              text,
    recurring_months    integer,
    CONSTRAINT uq_directive UNIQUE (tenant_id, reference),
    CONSTRAINT ck_directive_kind CHECK (kind IN ('AD', 'SB', 'STC', 'MOD'))
);

CREATE INDEX ix_directives_type ON camo.directives (aircraft_type_id);

CREATE TABLE camo.directive_applications (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    directive_id    uuid        NOT NULL REFERENCES camo.directives (id) ON DELETE CASCADE,
    aircraft_id     uuid        NOT NULL REFERENCES camo.aircraft (id),
    status          text        NOT NULL DEFAULT 'OPEN',
    complied_on     date,
    complied_ref    text,
    remark          text,
    CONSTRAINT uq_directive_application UNIQUE (directive_id, aircraft_id),
    CONSTRAINT ck_directive_status CHECK (status IN ('OPEN', 'COMPLIED', 'NOT_APPLICABLE', 'DEFERRED')),
    CONSTRAINT ck_directive_complied CHECK (status <> 'COMPLIED' OR complied_on IS NOT NULL)
);

CREATE INDEX ix_directive_applications_aircraft ON camo.directive_applications (aircraft_id, status);

-- ------------------------------------------------------------
--  5. Carnet de bord électronique : une entrée par vol.
--     Le carnet est ce qui alimente l'utilisation et ce qui
--     porte les défauts constatés par l'équipage.
-- ------------------------------------------------------------
CREATE TABLE camo.tech_log_entries (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    source_type        text        NOT NULL DEFAULT 'manual',
    source_ref         text,
    source_version     text,
    source_author      uuid,
    source_at          timestamptz NOT NULL DEFAULT now(),
    aircraft_id        uuid        NOT NULL REFERENCES camo.aircraft (id),
    leg_id             uuid        REFERENCES ops.legs (id),
    page_ref           text        NOT NULL,
    flown_on           date        NOT NULL,
    dep_icao           text,
    arr_icao           text,
    block_minutes      integer,
    air_minutes        integer,
    cycles             integer     NOT NULL DEFAULT 1,
    fuel_uplift_litres numeric(10,1),
    oil_added_litres   numeric(6,2),
    commander_id       uuid        REFERENCES crew.persons (id),
    engineer_id        uuid        REFERENCES crew.persons (id),
    status             text        NOT NULL DEFAULT 'OPEN',
    signed_at          timestamptz,
    remark             text,
    CONSTRAINT uq_tech_log_page UNIQUE (tenant_id, page_ref),
    CONSTRAINT ck_tech_log_status CHECK (status IN ('OPEN', 'SIGNED', 'CLOSED')),
    CONSTRAINT ck_tech_log_signed CHECK (status = 'OPEN' OR signed_at IS NOT NULL)
);

CREATE INDEX ix_tech_log_aircraft_day ON camo.tech_log_entries (aircraft_id, flown_on DESC);
CREATE INDEX ix_tech_log_leg ON camo.tech_log_entries (leg_id);

-- ------------------------------------------------------------
--  6. Défauts. Un défaut ouvert devient soit une réparation,
--     soit un report MEL — et le report pointe la ligne MEL.
-- ------------------------------------------------------------
CREATE TABLE camo.defects (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    source_type         text        NOT NULL DEFAULT 'manual',
    source_ref          text,
    source_version      text,
    source_author       uuid,
    source_at           timestamptz NOT NULL DEFAULT now(),
    aircraft_id         uuid        NOT NULL REFERENCES camo.aircraft (id),
    tech_log_entry_id   uuid        REFERENCES camo.tech_log_entries (id),
    ata_chapter         text,
    description         text        NOT NULL,
    reported_at         timestamptz NOT NULL DEFAULT now(),
    reported_by         uuid        REFERENCES crew.persons (id),
    status              text        NOT NULL DEFAULT 'OPEN',
    mel_item_id         uuid        REFERENCES camo.mel_items (id),
    corrective_action   text,
    closed_at           timestamptz,
    closed_by           uuid        REFERENCES crew.persons (id),
    CONSTRAINT ck_defect_status CHECK (status IN ('OPEN', 'DEFERRED', 'CLOSED')),
    CONSTRAINT ck_defect_deferred CHECK (status <> 'DEFERRED' OR mel_item_id IS NOT NULL),
    CONSTRAINT ck_defect_closed CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL)
);

CREATE INDEX ix_defects_aircraft_status ON camo.defects (aircraft_id, status);
CREATE INDEX ix_defects_techlog ON camo.defects (tech_log_entry_id);

-- ------------------------------------------------------------
--  7. Bibliothèque MEL : c'est elle qui porte l'intervalle de
--     rectification, donc l'échéance d'un report. Sans elle,
--     le prototype affichait « — » comme date d'échéance.
-- ------------------------------------------------------------
CREATE TABLE camo.mel_library (
    id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    source_type            text        NOT NULL DEFAULT 'manual',
    source_ref             text,
    source_version         text,
    source_author          uuid,
    source_at              timestamptz NOT NULL DEFAULT now(),
    aircraft_type_id       uuid        REFERENCES refdata.aircraft_types (id),
    item_ref               text        NOT NULL,
    ata_chapter            text        NOT NULL,
    title                  text        NOT NULL,
    mel_category           text        NOT NULL,
    rectification_days     integer,
    installed_quantity     integer,
    required_quantity      integer,
    placard_required       boolean     NOT NULL DEFAULT false,
    operational_procedure  text,
    maintenance_procedure  text,
    limitation             text,
    CONSTRAINT uq_mel_library_item UNIQUE (tenant_id, aircraft_type_id, item_ref),
    CONSTRAINT ck_mel_library_category CHECK (mel_category IN ('A', 'B', 'C', 'D'))
);

CREATE INDEX ix_mel_library_type ON camo.mel_library (aircraft_type_id, ata_chapter);

COMMENT ON COLUMN camo.mel_library.rectification_days IS
    'Intervalle Part-MEL : A = selon la remarque, B = 3 jours, C = 10 jours, D = 120 jours.';

-- ------------------------------------------------------------
--  8. Deux colonnes ajoutées à camo.mel_items : le lien vers la
--     bibliothèque et la trace de qui a reporté le défaut.
-- ------------------------------------------------------------
ALTER TABLE camo.mel_items
    ADD COLUMN mel_library_id uuid REFERENCES camo.mel_library (id),
    ADD COLUMN raised_by      uuid REFERENCES crew.persons (id),
    ADD COLUMN closed_by      uuid REFERENCES crew.persons (id),
    ADD COLUMN placard_fitted boolean NOT NULL DEFAULT false;
