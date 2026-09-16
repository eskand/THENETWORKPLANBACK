-- ============================================================
--  V7 — DOM2 Trip Support (country status, permits, services)
--  "no_instrument_known" is a first-class verdict: the product
--  never claims a permit is not required when it does not know.
-- ============================================================
CREATE TABLE tripsupport.country_status (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    source_type         text        NOT NULL DEFAULT 'engine',
    source_ref          text,
    source_version      text,
    source_author       uuid,
    source_at           timestamptz NOT NULL DEFAULT now(),
    leg_id              uuid        NOT NULL REFERENCES ops.legs (id),
    country_iso2        text        NOT NULL,
    firs                text[]      NOT NULL DEFAULT '{}',
    status              text        NOT NULL,
    instrument_ref      text,
    lead_time_hours     integer,
    deadline_at         timestamptz,
    asa_corpus_version  text,
    CONSTRAINT uq_country_status UNIQUE (leg_id, country_iso2),
    CONSTRAINT ck_country_status CHECK (status IN ('NOT_REQUIRED', 'PERMIT_REQUIRED', 'NO_INSTRUMENT_KNOWN'))
);

CREATE INDEX ix_country_status_leg ON tripsupport.country_status (leg_id);

CREATE TABLE tripsupport.permit_requests (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    source_type           text        NOT NULL DEFAULT 'manual',
    source_ref            text,
    source_version        text,
    source_author         uuid,
    source_at             timestamptz NOT NULL DEFAULT now(),
    leg_id                uuid        NOT NULL REFERENCES ops.legs (id),
    country_iso2          text        NOT NULL,
    kind                  text        NOT NULL,
    status                text        NOT NULL DEFAULT 'DRAFT',
    recipient             text,
    reference             text,
    sent_at               timestamptz,
    sent_by               uuid,
    acknowledged_at       timestamptz,
    confirmed_at          timestamptz,
    valid_from            timestamptz,
    valid_to              timestamptz,
    message_document_id   uuid,
    CONSTRAINT ck_permit_kind CHECK (kind IN ('OVERFLIGHT', 'LANDING')),
    CONSTRAINT ck_permit_status CHECK (status IN ('DRAFT', 'SENT', 'ACKNOWLEDGED', 'CONFIRMED', 'REFUSED'))
);

CREATE INDEX ix_permit_requests_leg ON tripsupport.permit_requests (leg_id);
CREATE INDEX ix_permit_requests_open ON tripsupport.permit_requests (tenant_id, status)
    WHERE status <> 'CONFIRMED';

CREATE TABLE tripsupport.service_requests (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    leg_id           uuid        NOT NULL REFERENCES ops.legs (id),
    station_icao     text        NOT NULL,
    service_type     text        NOT NULL,
    supplier_name    text,
    status           text        NOT NULL DEFAULT 'DRAFT',
    reference        text,
    sent_at          timestamptz,
    acknowledged_at  timestamptz,
    confirmed_at     timestamptz,
    remark           text,
    CONSTRAINT uq_service_request UNIQUE (leg_id, station_icao, service_type),
    CONSTRAINT ck_service_type CHECK (service_type IN ('HANDLING', 'FUEL', 'CATERING', 'CREW_TRANSPORT', 'PAX_TRANSPORT', 'CUSTOMS', 'DEICING', 'GAR', 'APIS')),
    CONSTRAINT ck_service_status CHECK (status IN ('DRAFT', 'SENT', 'ACKNOWLEDGED', 'CONFIRMED', 'REFUSED'))
);

CREATE INDEX ix_service_requests_leg ON tripsupport.service_requests (leg_id);
CREATE INDEX ix_service_requests_pending ON tripsupport.service_requests (tenant_id, status)
    WHERE status <> 'CONFIRMED';
