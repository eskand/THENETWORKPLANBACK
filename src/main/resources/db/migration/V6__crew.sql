-- ============================================================
--  V6 — DOM4 Crew (persons, leg assignments, FTL verdict)
--  The FTL verdict is stored with its reason so that the
--  dispatch board never has to re-run the engine to render.
-- ============================================================
CREATE TABLE crew.persons (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    staff_no         text        NOT NULL,
    first_name       text        NOT NULL,
    last_name        text        NOT NULL,
    main_role        text        NOT NULL,
    base_icao        text,
    licence_expiry   date,
    medical_expiry   date,
    training_expiry  date,
    active           boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_persons_staff_no UNIQUE (tenant_id, staff_no),
    CONSTRAINT ck_persons_role CHECK (main_role IN ('CAPTAIN', 'FIRST_OFFICER', 'CABIN', 'ENGINEER'))
);

CREATE INDEX ix_persons_tenant_role ON crew.persons (tenant_id, main_role);

CREATE TABLE crew.leg_assignments (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'roster',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    leg_id           uuid        NOT NULL REFERENCES ops.legs (id),
    person_id        uuid        NOT NULL REFERENCES crew.persons (id),
    seat             text        NOT NULL,
    ftl_verdict      text        NOT NULL DEFAULT 'UNKNOWN',
    ftl_reason       text,
    duty_start       timestamptz,
    duty_end         timestamptz,
    checked_in_at    timestamptz,
    checked_out_at   timestamptz,
    CONSTRAINT uq_leg_assignment_seat UNIQUE (leg_id, seat),
    CONSTRAINT ck_assignment_seat CHECK (seat IN ('CPT', 'FO', 'CABIN_1', 'CABIN_2', 'ENGINEER')),
    CONSTRAINT ck_assignment_ftl CHECK (ftl_verdict IN ('OK', 'WARNING', 'BREACH', 'UNKNOWN'))
);

CREATE INDEX ix_leg_assignments_leg ON crew.leg_assignments (leg_id);
CREATE INDEX ix_leg_assignments_person ON crew.leg_assignments (person_id);
