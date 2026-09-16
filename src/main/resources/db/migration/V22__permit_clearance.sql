-- ============================================================
--  V22 — NetPlus Services : Overflight & Landing Permit Clearance
--
--  Les données de référence du module : pays, règles de permis par
--  type de vol (item 8 OACI), conditions d'entrée, et les limites de
--  FIR qui permettent de savoir quels pays une route traverse.
--
--  Pourquoi valid_as_of existe sur chaque table
--  --------------------------------------------
--  Un préavis de permis, un contact d'autorité, une zone de conflit :
--  tout cela change. Le prototype affichait « RAD 2609 · NAVDB 2609A »
--  en légende de son en-tête — la notion de cycle existait déjà, mais
--  en décor, pas en donnée. Ici chaque ligne porte la date à laquelle
--  elle était exacte, et l'écran l'affiche à côté de la règle. Une
--  règle réglementaire sans date n'est pas une référence, c'est une
--  responsabilité.
--
--  Pourquoi la confiance est une colonne
--  -------------------------------------
--  Le prototype distingue déjà VERIFIED / TO_CONFIRM / INDICATIVE /
--  NOT_STATED sur chaque préavis. C'est une bonne idée et elle est
--  reprise telle quelle : un dispatcher doit savoir si « 48 h » est
--  lu dans l'AIP ou déduit d'un usage.
-- ============================================================

CREATE TABLE refdata.countries (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text NOT NULL DEFAULT 'refdata',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),

    name            text NOT NULL,
    flag_emoji      text,
    region          text NOT NULL,
    fir_primary     text,
    authority       text,

    -- required / conditional / exempt / restricted / closed
    permit_status   text NOT NULL,
    -- Le préavis générique, celui que le prototype montre hors filtre.
    notice          text,
    -- fast / med / slow / vlong / na — la classe qui colore le chiffre.
    notice_class    text,
    filing_methods  text[] NOT NULL DEFAULT '{}',
    caa_email       text,
    caa_website     text,
    aftn_address    text,
    fees            text,
    operational_note text,
    -- none / warn / danger
    alert_level     text NOT NULL DEFAULT 'none',

    valid_as_of     date NOT NULL,

    CONSTRAINT uq_country_name UNIQUE (name),
    CONSTRAINT ck_country_status CHECK (permit_status IN
        ('required', 'conditional', 'exempt', 'restricted', 'closed')),
    CONSTRAINT ck_country_alert CHECK (alert_level IN ('none', 'warn', 'danger')),
    CONSTRAINT ck_country_notice_class CHECK (notice_class IS NULL
        OR notice_class IN ('fast', 'med', 'slow', 'vlong', 'na'))
);

CREATE INDEX ix_countries_region ON refdata.countries (region);
CREATE INDEX ix_countries_fir ON refdata.countries (fir_primary);

COMMENT ON COLUMN refdata.countries.valid_as_of IS
    'Date à laquelle cette règle était exacte. Affichée à côté du préavis.';

-- ------------------------------------------------------------
--  Le préavis dépend du type de vol : un vol d''État et un vol
--  privé n''obéissent pas aux mêmes délais. Une ligne par pays et
--  par type item 8, avec le niveau de confiance de la source.
-- ------------------------------------------------------------

CREATE TABLE refdata.country_permit_rules (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text NOT NULL DEFAULT 'refdata',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),

    country_id      uuid NOT NULL REFERENCES refdata.countries(id) ON DELETE CASCADE,
    -- S / N / G / M / X — item 8 du plan de vol OACI.
    flight_type     text NOT NULL,
    lead_time       text,
    confidence      text NOT NULL,
    note            text,
    valid_as_of     date NOT NULL,

    CONSTRAINT uq_permit_rule UNIQUE (country_id, flight_type),
    CONSTRAINT ck_permit_flight_type CHECK (flight_type IN ('S', 'N', 'G', 'M', 'X')),
    CONSTRAINT ck_permit_confidence CHECK (confidence IN
        ('VERIFIED', 'TO_CONFIRM', 'INDICATIVE', 'NOT_STATED'))
);

-- ------------------------------------------------------------
--  Les conditions d''entrée : du texte réglementaire, section par
--  section (visas, entrée, aéronefs d''État, vols réguliers…).
--  Stocké en lignes plutôt qu''en un bloc, pour qu''un écran puisse
--  n''afficher que la section utile et citer sa source.
-- ------------------------------------------------------------

CREATE TABLE refdata.country_entry_requirements (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text NOT NULL DEFAULT 'refdata',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),

    country_id      uuid NOT NULL REFERENCES refdata.countries(id) ON DELETE CASCADE,
    -- visa / entry / state / scheduled / nonscheduled / customs / other
    section         text NOT NULL,
    heading         text,
    body            text NOT NULL,
    position        integer NOT NULL DEFAULT 0,
    valid_as_of     date NOT NULL
);

CREATE INDEX ix_entry_req_country ON refdata.country_entry_requirements (country_id, section, position);

-- ------------------------------------------------------------
--  Les limites de FIR.
--
--  C''est la table qui rend l''analyse de route possible : sans
--  géométrie, « quels pays cette route traverse » ne peut pas être
--  répondu, et le module se réduirait à un annuaire.
--
--  La bbox est stockée à part des polygones : elle sert de filtre
--  grossier avant le test point-dans-polygone, qui est cher. Une
--  route Paris–Dubaï traverse une trentaine de FIR sur 282.
-- ------------------------------------------------------------

CREATE TABLE refdata.fir_boundaries (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text NOT NULL DEFAULT 'refdata',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),

    fir_code        text NOT NULL,
    fir_name        text,
    country_name    text,
    lon_min         double precision NOT NULL,
    lat_min         double precision NOT NULL,
    lon_max         double precision NOT NULL,
    lat_max         double precision NOT NULL,
    -- Les anneaux, en [lon, lat], tels que l''annexe les fournit.
    polygons        jsonb NOT NULL,
    valid_as_of     date NOT NULL,

    CONSTRAINT uq_fir_code UNIQUE (fir_code)
);

CREATE INDEX ix_fir_bbox ON refdata.fir_boundaries (lon_min, lon_max, lat_min, lat_max);

COMMENT ON TABLE refdata.fir_boundaries IS
    'Limites FIR reprises de l''annexe A4 (NAVDB 2609A). Utilisées pour '
    'déterminer les FIR et les pays survolés par une route.';
