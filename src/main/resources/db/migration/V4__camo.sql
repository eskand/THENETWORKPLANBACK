-- ============================================================
--  V4 — DOM5 Airworthiness (aircraft, MEL)
-- ============================================================
CREATE TABLE camo.aircraft (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    source_type        text        NOT NULL DEFAULT 'manual',
    source_ref         text,
    source_version     text,
    source_author      uuid,
    source_at          timestamptz NOT NULL DEFAULT now(),
    registration       text        NOT NULL,
    aircraft_type_id   uuid        NOT NULL REFERENCES refdata.aircraft_types (id),
    home_base_icao     text,
    current_base_icao  text,
    status             text        NOT NULL DEFAULT 'SERVICEABLE',
    status_reason      text,
    status_since       timestamptz,
    hours_since_new    numeric(10,2),
    cycles_since_new   integer,
    next_check_label   text,
    next_check_due_at  timestamptz,
    CONSTRAINT uq_aircraft_registration UNIQUE (tenant_id, registration),
    CONSTRAINT ck_aircraft_status CHECK (status IN ('SERVICEABLE', 'MAINTENANCE', 'AOG'))
);

CREATE INDEX ix_aircraft_tenant_status ON camo.aircraft (tenant_id, status);
CREATE INDEX ix_aircraft_type ON camo.aircraft (aircraft_type_id);

CREATE TABLE camo.mel_items (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    aircraft_id      uuid        NOT NULL REFERENCES camo.aircraft (id),
    reference        text        NOT NULL,
    mel_category     text        NOT NULL,
    title            text        NOT NULL,
    limitation       text,
    raised_at        timestamptz NOT NULL DEFAULT now(),
    due_at           timestamptz,
    closed_at        timestamptz,
    blocks_dispatch  boolean     NOT NULL DEFAULT false,
    CONSTRAINT ck_mel_category CHECK (mel_category IN ('A', 'B', 'C', 'D'))
);

CREATE INDEX ix_mel_aircraft_open ON camo.mel_items (aircraft_id) WHERE closed_at IS NULL;
