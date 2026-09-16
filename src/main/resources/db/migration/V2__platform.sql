-- ============================================================
--  V2 — DOM9 Platform (minimal: tenants and users)
--  Authentication is not wired yet: users exist so that
--  signatures and acknowledgements have a real referent.
-- ============================================================
CREATE TABLE platform.tenants (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    code            text        NOT NULL,
    name            text        NOT NULL,
    authority       text        NOT NULL DEFAULT 'EASA',
    home_base_icao  text,
    active          boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_tenants_code UNIQUE (code),
    CONSTRAINT ck_tenants_authority CHECK (authority IN ('EASA', 'FAA', 'GCAA', 'ICAO'))
);

CREATE TABLE platform.users (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    login           text        NOT NULL,
    display_name    text        NOT NULL,
    initials        text,
    role            text        NOT NULL,
    active          boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_users_login UNIQUE (tenant_id, login),
    CONSTRAINT ck_users_role CHECK (role IN ('DISPATCHER', 'OCC_MANAGER', 'CREW', 'CAMO', 'SAFETY', 'SALES', 'ADMIN', 'GUEST'))
);

CREATE INDEX ix_users_tenant ON platform.users (tenant_id);
