-- ============================================================
--  V40 — le bac a sable du Simulation Center.
--
--  CE QUE L'ECRAN PROMET. « Sandbox only · zero writes to live
--  data ». C'est la promesse centrale du module : on injecte des
--  anomalies dans une copie du plan pour entrainer un dispatcher ou
--  eprouver un moteur d'optimisation, et le plan reel ne bouge pas.
--
--  COMMENT LE PROTOTYPE LA TIENT. Il copie le plan dans le
--  localStorage du navigateur, sous un prefixe a lui. Ca marche, et
--  ca a deux consequences : le scenario ne quitte jamais le poste qui
--  l'a genere, et la promesse repose sur une convention de nommage
--  de cle — si un jour une ecriture vise la mauvaise cle, rien ne
--  l'arrete.
--
--  COMMENT ON LA TIENT ICI. Un schema separe. sim.scenario_legs
--  n'est pas ops.legs : ce sont deux tables, dans deux schemas, et
--  aucune contrainte ne relie une etape simulee a une etape reelle.
--  Une ecriture de simulation ne PEUT pas atteindre le plan, ce n'est
--  plus une question de discipline.
--
--  LE LIEN VERS LE REEL EST UNE COPIE, PAS UNE REFERENCE.
--  source_leg_id garde l'identifiant de l'etape d'origine — sans cle
--  etrangere. Un scenario doit survivre a la suppression de l'etape
--  qu'il a copiee : c'est un instantane, et un instantane qui change
--  quand la source change n'en est pas un.
--
--  L'ALEA EST REPRODUCTIBLE. random_seed est stockee. Deux
--  generations avec la meme graine et le meme preset donnent le meme
--  scenario, ce qui est la difference entre un exercice qu'on peut
--  rejouer avec une autre equipe et une curiosite.
-- ============================================================

CREATE SCHEMA IF NOT EXISTS sim;

CREATE TABLE sim.scenarios (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    reference      text        NOT NULL,
    name           text        NOT NULL,
    difficulty     text        NOT NULL,
    -- La fenetre copiee. Le plan reel sur ces jours est fige dans
    -- scenario_legs : le scenario ne le relit jamais.
    horizon_from   date        NOT NULL,
    horizon_days   smallint    NOT NULL,
    -- La graine : meme graine, meme preset, meme scenario.
    random_seed    bigint      NOT NULL,
    status         text        NOT NULL DEFAULT 'DRAFT',
    -- Ce qui a ete DEMANDE par injecteur, et ce qui a pu etre POSE.
    -- Les deux, parce qu'ils different : un injecteur de ferry ne
    -- trouve pas toujours un creneau au sol assez long, et un ecran
    -- qui n'afficherait que la demande mentirait sur le scenario.
    requested      jsonb       NOT NULL DEFAULT '{}',
    applied        jsonb       NOT NULL DEFAULT '{}',
    -- L'etat du plan copie, avant injection : de quoi mesurer l'ecart.
    baseline       jsonb       NOT NULL DEFAULT '{}',
    remark         text,

    CONSTRAINT uq_scenario_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_scenario_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD', 'EXTREME', 'CUSTOM')),
    CONSTRAINT ck_scenario_status CHECK (status IN ('DRAFT', 'GENERATED', 'RUNNING', 'SOLVED', 'ARCHIVED')),
    CONSTRAINT ck_scenario_horizon CHECK (horizon_days BETWEEN 1 AND 14)
);

CREATE INDEX ix_scenarios_recent ON sim.scenarios (tenant_id, created_at DESC);

-- ------------------------------------------------------------
--  Les etapes du scenario. Une copie, jamais une reference.
-- ------------------------------------------------------------
CREATE TABLE sim.scenario_legs (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    scenario_id    uuid        NOT NULL REFERENCES sim.scenarios (id) ON DELETE CASCADE,

    -- L'etape reelle dont celle-ci est la copie. SANS cle etrangere,
    -- volontairement : l'instantane doit survivre a la disparition de
    -- sa source. Null pour une etape creee par un injecteur.
    source_leg_id  uuid,

    registration   text        NOT NULL,
    icao_type      text,
    flight_no      text        NOT NULL,
    dep_icao       text        NOT NULL,
    arr_icao       text        NOT NULL,
    std            timestamptz NOT NULL,
    sta            timestamptz NOT NULL,
    flight_type    text        NOT NULL DEFAULT 'PAX',
    pax_count      integer     NOT NULL DEFAULT 0,
    status         text        NOT NULL DEFAULT 'PLANNED',
    -- Le retard injecte, en minutes. Distinct des heures : std et sta
    -- portent deja le decalage, cette colonne dit d'ou il vient.
    delay_minutes  integer,
    -- Vrai quand l'injecteur a cree cette etape de toutes pieces.
    injected       boolean     NOT NULL DEFAULT false,

    CONSTRAINT ck_scenario_leg_times CHECK (sta > std),
    -- Une etape qui part d'ou elle arrive n'existe pas.
    CONSTRAINT ck_scenario_leg_route CHECK (dep_icao <> arr_icao)
);

CREATE INDEX ix_scenario_legs ON sim.scenario_legs (scenario_id, registration, std);

-- ------------------------------------------------------------
--  Les anomalies injectees
-- ------------------------------------------------------------
CREATE TABLE sim.anomalies (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    scenario_id    uuid        NOT NULL REFERENCES sim.scenarios (id) ON DELETE CASCADE,

    reference      text        NOT NULL,
    anomaly_type   text        NOT NULL,
    severity       text        NOT NULL,
    -- Le geste attendu du solveur. C'est la reponse du corrige : un
    -- scenario sans elle n'est qu'un plan degrade, pas un exercice.
    expected_fix   text        NOT NULL,
    registration   text,
    day_offset     smallint,
    note           text,
    -- Les etapes du scenario que cette anomalie touche.
    leg_ids        uuid[]      NOT NULL DEFAULT '{}',

    CONSTRAINT uq_anomaly_reference UNIQUE (scenario_id, reference),
    CONSTRAINT ck_anomaly_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'))
);

CREATE INDEX ix_anomalies_scenario ON sim.anomalies (scenario_id, severity);

COMMENT ON SCHEMA sim IS
    'Bac a sable du Simulation Center. Aucune table ici ne reference ops, crew ou camo : '
    'une ecriture de simulation ne peut pas atteindre le plan reel.';
