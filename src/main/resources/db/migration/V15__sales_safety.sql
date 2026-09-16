-- ============================================================
--  V15 — DOM7 Commercial et DOM6 Sécurité.
--
--  Constats d'audit corrigés :
--    « Sales : pas de moteur de prix ; devises additionnées
--      sans FX »          -> sales.quote_lines porte sa devise ET
--                            son taux de change au moment du devis
--    « SMS : pas d'ECCAIRS/ERC ; signatures non authentifiées »
--                         -> safety.occurrences porte les champs
--                            ECCAIRS, la matrice 5x5 est une table,
--                            et chaque signature est datée et
--                            attribuée
-- ============================================================

-- ------------------------------------------------------------
--  1. DOM7 — demandes, devis, lignes de devis.
-- ------------------------------------------------------------
CREATE TABLE sales.clients (
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
    name            text        NOT NULL,
    kind            text        NOT NULL DEFAULT 'CORPORATE',
    country_iso2    text,
    email           text,
    phone           text,
    payment_terms   text,
    currency        text        NOT NULL DEFAULT 'EUR',
    active          boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_client_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_client_kind CHECK (kind IN ('CORPORATE', 'BROKER', 'GOVERNMENT', 'PRIVATE', 'MEDICAL'))
);

CREATE TABLE sales.requests (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'manual',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    reference         text        NOT NULL,
    client_id         uuid        NOT NULL REFERENCES sales.clients (id),
    received_at       timestamptz NOT NULL DEFAULT now(),
    dep_icao          text        NOT NULL,
    arr_icao          text        NOT NULL,
    departure_at      timestamptz NOT NULL,
    return_at         timestamptz,
    pax_count         integer     NOT NULL DEFAULT 1,
    flight_type       text        NOT NULL DEFAULT 'PAX',
    aircraft_type_id  uuid        REFERENCES refdata.aircraft_types (id),
    status            text        NOT NULL DEFAULT 'NEW',
    feasibility       text        NOT NULL DEFAULT 'UNKNOWN',
    feasibility_note  text,
    remark            text,
    CONSTRAINT uq_request_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_request_status CHECK (status IN ('NEW', 'QUOTED', 'WON', 'LOST', 'CANCELLED')),
    CONSTRAINT ck_request_feasibility CHECK (feasibility IN ('FEASIBLE', 'NOT_FEASIBLE', 'CONDITIONAL', 'UNKNOWN')),
    CONSTRAINT ck_request_pax CHECK (pax_count >= 0)
);

CREATE INDEX ix_requests_status ON sales.requests (tenant_id, status);
CREATE INDEX ix_requests_departure ON sales.requests (tenant_id, departure_at);

COMMENT ON COLUMN sales.requests.feasibility IS
    'UNKNOWN tant que la faisabilite n a pas ete evaluee : ce n est pas FEASIBLE par defaut.';

CREATE TABLE sales.quotes (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'manual',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    request_id        uuid        NOT NULL REFERENCES sales.requests (id) ON DELETE CASCADE,
    reference         text        NOT NULL,
    version           integer     NOT NULL DEFAULT 1,
    currency          text        NOT NULL DEFAULT 'EUR',
    status            text        NOT NULL DEFAULT 'DRAFT',
    valid_until       date,
    sent_at           timestamptz,
    decided_at        timestamptz,
    decided_by        uuid        REFERENCES platform.users (id),
    trip_id           uuid        REFERENCES ops.trips (id),
    remark            text,
    CONSTRAINT uq_quote_reference UNIQUE (tenant_id, reference, version),
    CONSTRAINT ck_quote_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REFUSED', 'EXPIRED')),
    CONSTRAINT ck_quote_currency CHECK (char_length(currency) = 3)
);

CREATE INDEX ix_quotes_request ON sales.quotes (request_id);

-- Chaque ligne porte sa devise ET le taux retenu : additionner des euros et
-- des dollars sans taux est exactement ce que l'audit a releve.
CREATE TABLE sales.quote_lines (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    quote_id        uuid        NOT NULL REFERENCES sales.quotes (id) ON DELETE CASCADE,
    line_no         integer     NOT NULL,
    kind            text        NOT NULL,
    label           text        NOT NULL,
    quantity        numeric(12,3) NOT NULL DEFAULT 1,
    unit            text,
    unit_price      numeric(14,2) NOT NULL,
    currency        text        NOT NULL,
    fx_rate         numeric(14,6) NOT NULL DEFAULT 1,
    fx_rate_at      date,
    taxable         boolean     NOT NULL DEFAULT true,
    CONSTRAINT uq_quote_line UNIQUE (quote_id, line_no),
    CONSTRAINT ck_quote_line_kind CHECK (kind IN (
        'FLIGHT_HOUR', 'POSITIONING', 'HANDLING', 'FUEL', 'CATERING', 'CREW',
        'OVERFLIGHT', 'LANDING', 'PARKING', 'DEICING', 'TAX', 'DISCOUNT', 'OTHER')),
    CONSTRAINT ck_quote_line_currency CHECK (char_length(currency) = 3),
    CONSTRAINT ck_quote_line_fx CHECK (fx_rate > 0)
);

CREATE INDEX ix_quote_lines_quote ON sales.quote_lines (quote_id);

COMMENT ON COLUMN sales.quote_lines.fx_rate IS
    'Taux vers la devise du devis, fige au moment ou la ligne est ecrite. Un total est une somme de montants convertis, jamais de montants bruts.';

-- ------------------------------------------------------------
--  2. DOM6 — sécurité : occurrences, risques, actions,
--     campagnes de promotion, plan d'urgence.
-- ------------------------------------------------------------
CREATE TABLE safety.occurrences (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    source_type         text        NOT NULL DEFAULT 'manual',
    source_ref          text,
    source_version      text,
    source_author       uuid,
    source_at           timestamptz NOT NULL DEFAULT now(),
    reference           text        NOT NULL,
    occurred_at         timestamptz NOT NULL,
    reported_at         timestamptz NOT NULL DEFAULT now(),
    reported_by         uuid        REFERENCES crew.persons (id),
    anonymous           boolean     NOT NULL DEFAULT false,
    leg_id              uuid        REFERENCES ops.legs (id),
    aircraft_id         uuid        REFERENCES camo.aircraft (id),
    station_icao        text,
    category            text        NOT NULL,
    title               text        NOT NULL,
    narrative           text        NOT NULL,
    phase_of_flight     text,
    -- ECCAIRS / ADREP : l'audit relevait leur absence complète.
    eccairs_event_type  text,
    eccairs_occurrence_class text,
    eccairs_exported_at timestamptz,
    eccairs_reference   text,
    -- Risque : sévérité A..E et probabilité 1..5 (matrice 5x5)
    risk_severity       text,
    risk_probability    integer,
    risk_level          text,
    risk_assessed_at    timestamptz,
    risk_assessed_by    uuid        REFERENCES platform.users (id),
    status              text        NOT NULL DEFAULT 'REPORTED',
    closed_at           timestamptz,
    closed_by           uuid        REFERENCES platform.users (id),
    CONSTRAINT uq_occurrence_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_occurrence_category CHECK (category IN (
        'TECHNICAL', 'OPERATIONAL', 'GROUND', 'CABIN', 'SECURITY', 'MEDICAL',
        'ATC', 'WEATHER', 'BIRD_STRIKE', 'FUEL', 'OTHER')),
    CONSTRAINT ck_occurrence_status CHECK (status IN (
        'REPORTED', 'UNDER_REVIEW', 'RISK_ASSESSED', 'ACTIONS_OPEN', 'CLOSED')),
    CONSTRAINT ck_occurrence_severity CHECK (risk_severity IS NULL OR risk_severity IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_occurrence_probability CHECK (risk_probability IS NULL OR risk_probability BETWEEN 1 AND 5),
    CONSTRAINT ck_occurrence_risk_level CHECK (risk_level IS NULL OR risk_level IN (
        'ACCEPTABLE', 'TOLERABLE', 'UNACCEPTABLE')),
    CONSTRAINT ck_occurrence_assessed CHECK (
        risk_level IS NULL OR (risk_severity IS NOT NULL AND risk_probability IS NOT NULL))
);

CREATE INDEX ix_occurrences_status ON safety.occurrences (tenant_id, status);
CREATE INDEX ix_occurrences_date ON safety.occurrences (tenant_id, occurred_at DESC);

COMMENT ON COLUMN safety.occurrences.risk_level IS
    'Verdict de la matrice 5x5, ecrit au moment de l evaluation avec sa date et son evaluateur. Jamais recalcule a l affichage.';

CREATE TABLE safety.risk_matrix (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    severity        text        NOT NULL,
    probability     integer     NOT NULL,
    risk_level      text        NOT NULL,
    action_required text,
    CONSTRAINT uq_risk_cell UNIQUE (tenant_id, severity, probability),
    CONSTRAINT ck_matrix_severity CHECK (severity IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_matrix_probability CHECK (probability BETWEEN 1 AND 5),
    CONSTRAINT ck_matrix_level CHECK (risk_level IN ('ACCEPTABLE', 'TOLERABLE', 'UNACCEPTABLE'))
);

COMMENT ON TABLE safety.risk_matrix IS
    'La matrice 5x5 de l exploitant, en base : elle se modifie par une ligne, pas par un deploiement.';

CREATE TABLE safety.actions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    occurrence_id   uuid        REFERENCES safety.occurrences (id) ON DELETE CASCADE,
    reference       text        NOT NULL,
    title           text        NOT NULL,
    detail          text,
    owner_user_id   uuid        REFERENCES platform.users (id),
    due_on          date,
    status          text        NOT NULL DEFAULT 'OPEN',
    completed_on    date,
    effectiveness   text,
    CONSTRAINT uq_action_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_action_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_action_effectiveness CHECK (effectiveness IS NULL OR effectiveness IN (
        'NOT_ASSESSED', 'EFFECTIVE', 'PARTIALLY_EFFECTIVE', 'NOT_EFFECTIVE')),
    CONSTRAINT ck_action_completed CHECK (status <> 'COMPLETED' OR completed_on IS NOT NULL)
);

CREATE INDEX ix_actions_open ON safety.actions (tenant_id, status, due_on);

CREATE TABLE safety.campaigns (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    reference       text        NOT NULL,
    title           text        NOT NULL,
    theme           text        NOT NULL,
    message         text,
    starts_on       date        NOT NULL,
    ends_on         date,
    audience        text        NOT NULL DEFAULT 'ALL',
    status          text        NOT NULL DEFAULT 'PLANNED',
    acknowledgement_required boolean NOT NULL DEFAULT false,
    CONSTRAINT uq_campaign_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_campaign_status CHECK (status IN ('PLANNED', 'RUNNING', 'CLOSED')),
    CONSTRAINT ck_campaign_audience CHECK (audience IN ('ALL', 'FLIGHT_CREW', 'CABIN', 'MAINTENANCE', 'GROUND', 'OFFICE'))
);

CREATE TABLE safety.campaign_acknowledgements (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    campaign_id     uuid        NOT NULL REFERENCES safety.campaigns (id) ON DELETE CASCADE,
    person_id       uuid        NOT NULL REFERENCES crew.persons (id),
    acknowledged_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_campaign_ack UNIQUE (campaign_id, person_id)
);

-- ------------------------------------------------------------
--  3. Plan d'urgence (ERP) : le plan, ses rôles, ses activations.
-- ------------------------------------------------------------
CREATE TABLE safety.erp_plans (
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
    revision        text        NOT NULL,
    approved_on     date,
    review_due_on   date,
    summary         text,
    CONSTRAINT uq_erp_plan UNIQUE (tenant_id, code, revision)
);

CREATE TABLE safety.erp_roles (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    plan_id         uuid        NOT NULL REFERENCES safety.erp_plans (id) ON DELETE CASCADE,
    role_code       text        NOT NULL,
    role_title      text        NOT NULL,
    holder_user_id  uuid        REFERENCES platform.users (id),
    deputy_user_id  uuid        REFERENCES platform.users (id),
    phone           text,
    responsibilities text,
    call_order      integer     NOT NULL DEFAULT 1,
    CONSTRAINT uq_erp_role UNIQUE (plan_id, role_code)
);

CREATE TABLE safety.erp_activations (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'manual',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),
    plan_id         uuid        NOT NULL REFERENCES safety.erp_plans (id),
    kind            text        NOT NULL,
    reference       text        NOT NULL,
    leg_id          uuid        REFERENCES ops.legs (id),
    activated_at    timestamptz NOT NULL DEFAULT now(),
    activated_by    uuid        REFERENCES platform.users (id),
    stood_down_at   timestamptz,
    situation       text,
    CONSTRAINT uq_erp_activation UNIQUE (tenant_id, reference),
    CONSTRAINT ck_erp_activation_kind CHECK (kind IN ('EXERCISE', 'REAL', 'STANDBY'))
);

CREATE INDEX ix_erp_activations_open ON safety.erp_activations (tenant_id) WHERE stood_down_at IS NULL;
