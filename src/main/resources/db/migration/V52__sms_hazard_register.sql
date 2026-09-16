-- ============================================================
--  V52 — le registre des dangers, les REX, les questions, le centre
--        de notifications.
--
--  POURQUOI. Quatre onglets du systeme de gestion de la securite de
--  l'annexe A4 n'avaient aucune table derriere eux.
--
--  LE REGISTRE DES DANGERS. safety.risk_matrix tient la MATRICE — la
--  grille 5x5 qui dit ce que vaut un C3. Elle ne tient aucun danger.
--  Le registre est l'autre chose : ce que l'exploitant a identifie,
--  ce qu'il en craint, et ce qu'il a mis en place pour le reduire.
--  Sans lui, l'onglet « Risk register » n'a rien a montrer et la
--  composante 2 de l'annexe 19 de l'OACI n'est pas tenue.
--
--  LE RISQUE INITIAL ET LE RISQUE RESIDUEL. Les deux sont stockes,
--  pas seulement le second. La difference EST la mesure de ce que les
--  barrieres apportent ; ne garder que le residuel rend impossible de
--  montrer qu'une barriere sert a quelque chose.
--
--  L'INDEX N'EST PAS STOCKE. severite x probabilite se calcule. Un
--  index stocke se desynchronise de ses deux facteurs des la premiere
--  reevaluation, et c'est le chiffre qu'un auditeur regarde.
--
--  LES QUESTIONS AU DECLARANT. Cinq colonnes sur l'occurrence plutot
--  qu'une table : une occurrence porte au plus une question ouverte a
--  la fois, et l'onglet « Action required » du declarant n'est que la
--  liste de celles qui attendent sa reponse.
--
--  LES REX. Le retour d'experience n'est pas une occurrence : rien
--  n'a mal tourne. C'est une lecon que quelqu'un a voulu transmettre,
--  et elle se publie a la flotte. Table a part, pour que le registre
--  des occurrences reste ce qu'il est.
-- ============================================================

-- ------------------------------------------------------------
--  1. Le registre des dangers
-- ------------------------------------------------------------
CREATE TABLE safety.hazards (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    reference   text NOT NULL,
    hazard      text NOT NULL,
    -- La consequence credible, pas la pire imaginable : c'est elle
    -- qu'on cote.
    consequence text,
    domain      text NOT NULL,
    category    text,
    -- reactive : ne d'une occurrence. proactive : d'un audit ou du
    -- suivi. predictive : d'une analyse de tendance.
    identification text NOT NULL DEFAULT 'proactive',

    -- Avant barrieres.
    severity_initial   text     NOT NULL,
    likelihood_initial smallint NOT NULL,
    -- Apres barrieres.
    severity_residual   text     NOT NULL,
    likelihood_residual smallint NOT NULL,

    owner       text,
    review_on   date,
    status      text NOT NULL DEFAULT 'open',
    notes       text,

    CONSTRAINT uq_hazard_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_hazard_sev_i CHECK (severity_initial IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_hazard_sev_r CHECK (severity_residual IN ('A', 'B', 'C', 'D', 'E')),
    CONSTRAINT ck_hazard_like_i CHECK (likelihood_initial BETWEEN 1 AND 5),
    CONSTRAINT ck_hazard_like_r CHECK (likelihood_residual BETWEEN 1 AND 5),
    CONSTRAINT ck_hazard_identification CHECK (
        identification IN ('reactive', 'proactive', 'predictive')),
    CONSTRAINT ck_hazard_status CHECK (
        status IN ('open', 'mitigating', 'monitored', 'closed')),
    -- Les barrieres reduisent le risque ; elles ne l'augmentent pas.
    -- Un residuel pire que l'initial est une erreur de saisie, pas un
    -- resultat, et la base le dit au lieu de l'afficher.
    CONSTRAINT ck_hazard_residual_not_worse CHECK (
        (CASE severity_residual WHEN 'A' THEN 5 WHEN 'B' THEN 4 WHEN 'C' THEN 3
                                WHEN 'D' THEN 2 ELSE 1 END) * likelihood_residual
        <= (CASE severity_initial WHEN 'A' THEN 5 WHEN 'B' THEN 4 WHEN 'C' THEN 3
                                  WHEN 'D' THEN 2 ELSE 1 END) * likelihood_initial)
);

CREATE INDEX ix_hazards ON safety.hazards (tenant_id, status);

CREATE TABLE safety.hazard_controls (
    id          uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid    NOT NULL REFERENCES platform.tenants (id),
    hazard_id   uuid    NOT NULL REFERENCES safety.hazards (id) ON DELETE CASCADE,
    description text    NOT NULL,
    -- preventive : empeche l'evenement. recovery : en limite les
    -- consequences. Les deux ne valent pas la meme chose.
    control_type text   NOT NULL DEFAULT 'preventive',
    owner       text,
    status      text    NOT NULL DEFAULT 'planned',
    sort_order  integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_control_type CHECK (control_type IN ('preventive', 'recovery')),
    CONSTRAINT ck_control_status CHECK (status IN ('planned', 'in-place', 'withdrawn'))
);

CREATE INDEX ix_hazard_controls ON safety.hazard_controls (hazard_id);

CREATE TABLE safety.hazard_occurrences (
    hazard_id     uuid NOT NULL REFERENCES safety.hazards (id) ON DELETE CASCADE,
    occurrence_id uuid NOT NULL REFERENCES safety.occurrences (id) ON DELETE CASCADE,
    PRIMARY KEY (hazard_id, occurrence_id)
);

-- ------------------------------------------------------------
--  2. La question posee au declarant
-- ------------------------------------------------------------
-- Le declarant qui n est pas navigant.
--
-- reported_by pointe sur crew.persons. Un regulateur, un agent
-- d escale ou un mecanicien declare aussi, et son nom n a nulle part
-- ou aller : la colonne reste vide et le registre perd le declarant.
-- Le nom en clair le garde, quel que soit le registre d origine.
ALTER TABLE safety.occurrences
    ADD COLUMN reporter_name text,
    ADD COLUMN reporter_role text;

ALTER TABLE safety.occurrences
    ADD COLUMN query_text        text,
    ADD COLUMN query_asked_at    timestamptz,
    ADD COLUMN query_asked_by    text,
    ADD COLUMN query_answer      text,
    ADD COLUMN query_answered_at timestamptz;

ALTER TABLE safety.occurrences
    -- Une question repondue n'est plus une question ouverte, et une
    -- reponse sans question n'existe pas.
    ADD CONSTRAINT ck_occurrence_query CHECK (
        query_answer IS NULL OR query_text IS NOT NULL);

-- ------------------------------------------------------------
--  3. Le retour d'experience
-- ------------------------------------------------------------
CREATE TABLE safety.rex (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    reference   text NOT NULL,
    title       text NOT NULL,
    category    text NOT NULL,
    phase       text,
    aircraft_type text,
    location    text,
    -- Ce qui s'est passe, en entier. Un REX ampute de son recit n'est
    -- plus un retour d'experience, c'est un slogan.
    narrative   text NOT NULL,
    recommendation text,
    author_name text,
    author_role text,
    -- named ou anonymous. Un REX anonyme ne porte pas de nom : la
    -- colonne reste vide, elle n'est pas seulement masquee a l'ecran.
    attribution text NOT NULL DEFAULT 'named',
    scope       text NOT NULL DEFAULT 'fleet',
    published_on date,
    status      text NOT NULL DEFAULT 'draft',

    CONSTRAINT uq_rex_ref UNIQUE (tenant_id, reference),
    CONSTRAINT ck_rex_attribution CHECK (attribution IN ('named', 'anonymous')),
    CONSTRAINT ck_rex_status CHECK (status IN ('draft', 'review', 'published', 'withdrawn')),
    CONSTRAINT ck_rex_anonymous CHECK (attribution <> 'anonymous' OR author_name IS NULL),
    CONSTRAINT ck_rex_published CHECK (status <> 'published' OR published_on IS NOT NULL)
);

CREATE INDEX ix_rex ON safety.rex (tenant_id, status, published_on DESC);

CREATE TABLE safety.rex_lessons (
    id         uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    rex_id     uuid    NOT NULL REFERENCES safety.rex (id) ON DELETE CASCADE,
    lesson     text    NOT NULL,
    sort_order integer NOT NULL DEFAULT 0
);

CREATE INDEX ix_rex_lessons ON safety.rex_lessons (rex_id);

-- Qui l'a lu. Le compteur de lectures se compte ici plutot que de
-- s'incrementer dans une colonne : un compteur qui ne sait pas QUI a
-- lu ne peut pas dire qui ne l'a pas fait.
CREATE TABLE safety.rex_reads (
    rex_id     uuid        NOT NULL REFERENCES safety.rex (id) ON DELETE CASCADE,
    reader     text        NOT NULL,
    read_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (rex_id, reader)
);

-- ------------------------------------------------------------
--  4. Le centre de notifications
-- ------------------------------------------------------------
CREATE TABLE safety.notifications (
    id        uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id uuid        NOT NULL REFERENCES platform.tenants (id),
    at        timestamptz NOT NULL DEFAULT now(),
    kind      text        NOT NULL,
    title     text        NOT NULL,
    body      text        NOT NULL,
    severity  text        NOT NULL DEFAULT 'info',
    -- La reference de l'objet concerne, en clair : « OCC-2026-0113 ».
    entity_ref text,
    domain    text,
    read_at   timestamptz,

    CONSTRAINT ck_notification_kind CHECK (
        kind IN ('occurrence', 'capa', 'audit', 'promotion', 'finding', 'risk')),
    CONSTRAINT ck_notification_severity CHECK (
        severity IN ('critical', 'high', 'medium', 'info'))
);

CREATE INDEX ix_notifications ON safety.notifications (tenant_id, at DESC);
CREATE INDEX ix_notifications_unread ON safety.notifications (tenant_id)
    WHERE read_at IS NULL;
