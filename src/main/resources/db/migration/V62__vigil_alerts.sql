-- ============================================================
--  V62 — Les alertes VIGIL, avec le cycle de vie de l'annexe
--
--  Le module VIGIL de l'annexe (prototype l. 97841-99460) tient ses
--  alertes dans localStorage sous TNP_VIGIL_V1, avec une identite
--  deterministe (regle | immatriculation | vol | date), quatre
--  gravites (critical / high / warning / info) et cinq etats :
--  OPEN -> ACKNOWLEDGED -> IN PROGRESS -> RESOLVED / DISMISSED, plus la
--  resolution automatique quand la condition n'est plus observee.
--
--  `ops.alerts` (V5) est le mur d'alertes FR15 : trois gravites, un
--  simple accuse. Ce n'est pas le meme objet — la cloche de l'en-tete
--  et le panneau VIGIL sont deux choses chez l'annexe aussi — et
--  elargir ses contraintes aurait change le sens des lignes qu'il
--  porte deja. VIGIL a donc sa table, avec son vocabulaire a lui.
--
--  La signature est unique par tenant : c'est elle qui permet au
--  balayage de METTRE A JOUR une alerte au lieu d'en creer une a
--  chaque passage — la dedoublonnage de ALERTS.upsert (l. 98695).
-- ============================================================
CREATE TABLE ops.vigil_alerts (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text        NOT NULL DEFAULT 'engine',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),

    signature       text        NOT NULL,
    rule            text        NOT NULL,
    name            text        NOT NULL,
    category        text        NOT NULL,
    leg_id          uuid        REFERENCES ops.legs (id),
    flight_no       text,
    registration    text,
    flight_date     date        NOT NULL,
    severity        text        NOT NULL,
    risk            integer,
    method          text        NOT NULL DEFAULT 'rule',
    why             text        NOT NULL,
    impact          text,
    action          text,
    status          text        NOT NULL DEFAULT 'OPEN',
    last_seen_at    timestamptz NOT NULL DEFAULT now(),
    reopened_at     timestamptz,
    resolved_at     timestamptz,
    resolved_by     text,
    updated_by      uuid,

    CONSTRAINT uq_vigil_alerts_signature UNIQUE (tenant_id, signature),
    CONSTRAINT ck_vigil_alerts_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'WARNING', 'INFO')),
    CONSTRAINT ck_vigil_alerts_status   CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'DISMISSED'))
);

CREATE INDEX ix_vigil_alerts_active ON ops.vigil_alerts (tenant_id, severity)
    WHERE status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS');

COMMENT ON TABLE ops.vigil_alerts IS
    'VIGIL operational-intelligence alerts — deterministic signature, four severities, five-state lifecycle';
COMMENT ON COLUMN ops.vigil_alerts.signature IS
    'rule|registration|flightNo|date — the identity ALERTS.upsert() dedupes on';
COMMENT ON COLUMN ops.vigil_alerts.resolved_by IS
    'Who or what resolved it — "auto — condition no longer observed" for the scan';
