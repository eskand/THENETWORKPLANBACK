-- ============================================================
--  V9 — DOM4 Crew, suite : qualifications, absences, périodes
--  de service, roster, formation.
--
--  L'audit (B_crew_camo) relevait : aucune persistance des
--  dossiers d'équipage, 15 dossiers de formation sur 101,
--  TR_NOW figé, quatre calculs de vacation concurrents.
--  Ici il n'y a qu'une source : ces tables. Les compteurs FTL
--  sont agrégés depuis crew.duty_periods, jamais saisis.
-- ============================================================

CREATE TABLE crew.qualifications (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'manual',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    person_id         uuid        NOT NULL REFERENCES crew.persons (id),
    aircraft_type_id  uuid        REFERENCES refdata.aircraft_types (id),
    kind              text        NOT NULL,
    level             text,
    valid_from        date,
    valid_to          date,
    reference         text,
    CONSTRAINT uq_qualification UNIQUE (person_id, kind, aircraft_type_id),
    CONSTRAINT ck_qualification_kind CHECK (kind IN (
        'TYPE_RATING', 'LINE_CHECK', 'OPC', 'LPC', 'SEP', 'CRM',
        'DANGEROUS_GOODS', 'ETOPS', 'LVO', 'ROUTE_COMPETENCE')),
    CONSTRAINT ck_qualification_level CHECK (level IS NULL OR level IN (
        'PIC', 'SIC', 'PICUS', 'INSTRUCTOR', 'EXAMINER')),
    CONSTRAINT ck_qualification_window CHECK (valid_to IS NULL OR valid_from IS NULL OR valid_to >= valid_from)
);

CREATE INDEX ix_qualifications_person ON crew.qualifications (person_id);
CREATE INDEX ix_qualifications_expiry ON crew.qualifications (tenant_id, valid_to);

COMMENT ON TABLE crew.qualifications IS
    'Une qualification par (personne, nature, type avion). TYPE_RATING porte le type, SEP/CRM/DG non.';

CREATE TABLE crew.absences (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    person_id       uuid        NOT NULL REFERENCES crew.persons (id),
    kind            text        NOT NULL,
    starts_on       date        NOT NULL,
    ends_on         date        NOT NULL,
    reason          text,
    CONSTRAINT ck_absence_kind CHECK (kind IN ('LEAVE', 'SICK', 'UNAVAILABLE', 'TRAINING', 'OFFICE')),
    CONSTRAINT ck_absence_window CHECK (ends_on >= starts_on)
);

CREATE INDEX ix_absences_person_window ON crew.absences (person_id, starts_on, ends_on);

-- ------------------------------------------------------------
--  Périodes de service : la seule base des compteurs FTL.
--  block_minutes est le temps de vol (bloc à bloc) de la période
--  quand elle est une vacation de vol, NULL sinon.
-- ------------------------------------------------------------
CREATE TABLE crew.duty_periods (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'roster',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    person_id       uuid        NOT NULL REFERENCES crew.persons (id),
    leg_id          uuid        REFERENCES ops.legs (id),
    kind            text        NOT NULL,
    report_at       timestamptz NOT NULL,
    off_duty_at     timestamptz NOT NULL,
    block_minutes   integer,
    sectors         integer     NOT NULL DEFAULT 0,
    remark          text,
    CONSTRAINT ck_duty_kind CHECK (kind IN (
        'FLIGHT_DUTY', 'STANDBY', 'POSITIONING', 'TRAINING', 'OFFICE', 'REST', 'OFF')),
    CONSTRAINT ck_duty_window CHECK (off_duty_at > report_at),
    CONSTRAINT ck_duty_block CHECK (block_minutes IS NULL OR block_minutes >= 0)
);

CREATE INDEX ix_duty_person_window ON crew.duty_periods (person_id, report_at);
CREATE INDEX ix_duty_tenant_window ON crew.duty_periods (tenant_id, report_at);
CREATE INDEX ix_duty_leg ON crew.duty_periods (leg_id);

COMMENT ON COLUMN crew.duty_periods.block_minutes IS
    'Temps de vol bloc a bloc. Les cumuls 7/28/365 jours sont des SUM sur cette colonne, jamais une saisie.';

-- ------------------------------------------------------------
--  Roster : une version porte un état (brouillon / publié).
--  Publier fige la version ; on ne modifie jamais une version
--  publiée, on en ouvre une nouvelle.
-- ------------------------------------------------------------
CREATE TABLE crew.roster_versions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    label           text        NOT NULL,
    period_start    date        NOT NULL,
    period_end      date        NOT NULL,
    status          text        NOT NULL DEFAULT 'DRAFT',
    published_at    timestamptz,
    published_by    uuid        REFERENCES platform.users (id),
    CONSTRAINT uq_roster_period UNIQUE (tenant_id, period_start, period_end, label),
    CONSTRAINT ck_roster_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_roster_window CHECK (period_end >= period_start),
    CONSTRAINT ck_roster_published CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)
);

CREATE TABLE crew.roster_entries (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    source_type        text        NOT NULL DEFAULT 'roster',
    source_ref         text,
    source_version     text,
    source_author      uuid,
    source_at          timestamptz NOT NULL DEFAULT now(),
    roster_version_id  uuid        NOT NULL REFERENCES crew.roster_versions (id) ON DELETE CASCADE,
    person_id          uuid        NOT NULL REFERENCES crew.persons (id),
    duty_date          date        NOT NULL,
    code               text        NOT NULL,
    duty_period_id     uuid        REFERENCES crew.duty_periods (id),
    leg_id             uuid        REFERENCES ops.legs (id),
    remark             text,
    CONSTRAINT uq_roster_entry UNIQUE (roster_version_id, person_id, duty_date, code),
    CONSTRAINT ck_roster_code CHECK (code IN (
        'FLT', 'SBY', 'POS', 'TRG', 'OFF', 'LVE', 'SICK', 'OFFICE', 'RES'))
);

CREATE INDEX ix_roster_entries_version_day ON crew.roster_entries (roster_version_id, duty_date);
CREATE INDEX ix_roster_entries_person ON crew.roster_entries (person_id, duty_date);

-- ------------------------------------------------------------
--  Formation : le catalogue, les sessions, les inscriptions,
--  et le dossier de chaque personne.
-- ------------------------------------------------------------
CREATE TABLE crew.training_courses (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'manual',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    code              text        NOT NULL,
    title             text        NOT NULL,
    category          text        NOT NULL,
    validity_months   integer,
    mandatory         boolean     NOT NULL DEFAULT true,
    authority_ref     text,
    CONSTRAINT uq_training_course UNIQUE (tenant_id, code),
    CONSTRAINT ck_training_category CHECK (category IN (
        'INITIAL', 'RECURRENT', 'CRM', 'DANGEROUS_GOODS', 'SEP', 'SIMULATOR',
        'LINE_TRAINING', 'SECURITY', 'GROUND'))
);

CREATE TABLE crew.training_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    course_id       uuid        NOT NULL REFERENCES crew.training_courses (id),
    starts_at       timestamptz NOT NULL,
    ends_at         timestamptz NOT NULL,
    location        text,
    capacity        integer     NOT NULL DEFAULT 12,
    instructor_id   uuid        REFERENCES crew.persons (id),
    status          text        NOT NULL DEFAULT 'PLANNED',
    CONSTRAINT ck_session_status CHECK (status IN ('PLANNED', 'RUNNING', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_session_window CHECK (ends_at > starts_at),
    CONSTRAINT ck_session_capacity CHECK (capacity > 0)
);

CREATE INDEX ix_training_sessions_window ON crew.training_sessions (tenant_id, starts_at);

CREATE TABLE crew.training_enrolments (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    session_id      uuid        NOT NULL REFERENCES crew.training_sessions (id) ON DELETE CASCADE,
    person_id       uuid        NOT NULL REFERENCES crew.persons (id),
    status          text        NOT NULL DEFAULT 'BOOKED',
    score           numeric(5,2),
    CONSTRAINT uq_enrolment UNIQUE (session_id, person_id),
    CONSTRAINT ck_enrolment_status CHECK (status IN ('BOOKED', 'ATTENDED', 'NO_SHOW', 'CANCELLED', 'FAILED'))
);

CREATE INDEX ix_enrolments_person ON crew.training_enrolments (person_id);

CREATE TABLE crew.training_records (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    person_id       uuid        NOT NULL REFERENCES crew.persons (id),
    course_id       uuid        NOT NULL REFERENCES crew.training_courses (id),
    session_id      uuid        REFERENCES crew.training_sessions (id),
    completed_on    date        NOT NULL,
    valid_to        date,
    score           numeric(5,2),
    instructor_id   uuid        REFERENCES crew.persons (id),
    reference       text,
    CONSTRAINT uq_training_record UNIQUE (person_id, course_id, completed_on)
);

CREATE INDEX ix_training_records_person ON crew.training_records (person_id, valid_to);
CREATE INDEX ix_training_records_expiry ON crew.training_records (tenant_id, valid_to);

COMMENT ON TABLE crew.training_records IS
    'Dossier de formation. valid_to est calcule a l enregistrement (completed_on + validity_months), jamais a l affichage.';
