-- ============================================================
--  V37 — les trois composants du SMS qui manquaient.
--
--  POURQUOI. L'ecran Safety Manager de l'annexe A4 tient sur dix
--  onglets, et son tableau de bord affiche quatre choses que rien ici
--  ne portait :
--
--    · le PROGRAMME D'AUDIT et ses constats — c'est le composant 3 de
--      l'annexe 19 de l'OACI, « assurance du niveau de securite ».
--      Sans lui, « 4 open audit findings » n'a aucune source ;
--    · les INDICATEURS (SPI) avec leur cible et leur seuil d'alerte.
--      Le prototype en publie huit, et le tableau de bord les affiche
--      contre leur cible. Une valeur sans cible ne dit rien ;
--    · les ENQUETES ouvertes sur un evenement ;
--    · la GESTION DU CHANGEMENT (ORO.GEN.200(a)(3)) : le panneau de
--      responsabilite du prototype annonce « 2 active » et rien ne
--      les tenait.
--
--  CE QUI EXISTAIT DEJA ET NE BOUGE PAS. safety.occurrences — qui
--  porte meme les champs ECCAIRS/ADREP que l'audit relevait absents
--  du prototype — safety.actions, safety.campaigns, safety.risk_matrix
--  et les trois tables ERP.
--
--  L'INDICE DE RISQUE. Le tableau de bord range les occurrences en
--  « intolerable >= 15 / eleve 10-14 / tolerable <= 9 ». C'est un
--  indice numerique, et la matrice de l'exploitant n'en portait pas :
--  elle donnait un NIVEAU pour un couple (severite, probabilite),
--  jamais un nombre. On ajoute l'indice sur la cellule plutot que de
--  le recalculer dans chaque ecran : le niveau et l'indice sortent
--  alors de la meme ligne et ne peuvent pas se contredire.
-- ============================================================

-- ------------------------------------------------------------
--  L'indice, sur la matrice de l'exploitant
-- ------------------------------------------------------------
ALTER TABLE safety.risk_matrix
    ADD COLUMN severity_value smallint,
    ADD COLUMN risk_index     smallint;

-- Severite A catastrophique = 5, E negligeable = 1 ; l'indice est le
-- produit par la probabilite, soit 1 a 25. C'est la convention de
-- l'OACI (Doc 9859), pas une invention locale.
UPDATE safety.risk_matrix SET severity_value = CASE severity
    WHEN 'A' THEN 5 WHEN 'B' THEN 4 WHEN 'C' THEN 3 WHEN 'D' THEN 2 ELSE 1 END;
UPDATE safety.risk_matrix SET risk_index = severity_value * probability;

ALTER TABLE safety.risk_matrix
    ALTER COLUMN severity_value SET NOT NULL,
    ALTER COLUMN risk_index     SET NOT NULL,
    ADD CONSTRAINT ck_risk_index CHECK (risk_index = severity_value * probability);

-- ------------------------------------------------------------
--  Programme d'audit
-- ------------------------------------------------------------
CREATE TABLE safety.audits (
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
    -- Le referentiel audite : IS-BAO, EASA ORO.GEN.200, ICAO Annex 19,
    -- Part-CAMO, ou l'interne. Il decide de la grille de constats.
    standard       text        NOT NULL,
    scope          text,
    -- Interne ou externe : un audit externe ne se cloture pas tout seul.
    auditor        text,
    external_audit boolean     NOT NULL DEFAULT false,
    planned_on     date        NOT NULL,
    conducted_on   date,
    closed_on      date,
    status         text        NOT NULL DEFAULT 'PLANNED',
    -- Le score de conformite, quand l'audit en produit un. Null tant que
    -- l'audit n'a pas eu lieu : zero voudrait dire « tout est non conforme ».
    score_percent  smallint,
    remark         text,

    CONSTRAINT uq_audit_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_audit_status CHECK (status IN ('PLANNED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_audit_score CHECK (score_percent IS NULL OR score_percent BETWEEN 0 AND 100),
    -- Un audit conduit a une date, cloture a une autre, jamais avant.
    CONSTRAINT ck_audit_dates CHECK (
        (conducted_on IS NULL OR conducted_on >= planned_on - 365)
        AND (closed_on IS NULL OR conducted_on IS NULL OR closed_on >= conducted_on))
);

CREATE INDEX ix_audits_planned ON safety.audits (tenant_id, planned_on);

CREATE TABLE safety.audit_findings (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    audit_id       uuid        NOT NULL REFERENCES safety.audits (id) ON DELETE CASCADE,
    reference      text        NOT NULL,
    -- Niveau 1 : la securite est compromise, correction immediate.
    -- Niveau 2 : ecart qui laisse un delai. Observation : sans ecart.
    level          text        NOT NULL,
    title          text        NOT NULL,
    detail         text,
    -- La reference reglementaire enfreinte : c'est elle qui rend le
    -- constat opposable, et un constat sans reference est une opinion.
    requirement    text,
    raised_on      date        NOT NULL,
    due_on         date,
    closed_on      date,
    -- L'action corrective ouverte pour ce constat, quand il y en a une.
    action_id      uuid        REFERENCES safety.actions (id),

    CONSTRAINT uq_audit_finding_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_audit_finding_level CHECK (level IN ('LEVEL_1', 'LEVEL_2', 'OBSERVATION')),
    CONSTRAINT ck_audit_finding_dates CHECK (closed_on IS NULL OR closed_on >= raised_on)
);

CREATE INDEX ix_audit_findings_open
    ON safety.audit_findings (tenant_id, audit_id) WHERE closed_on IS NULL;

-- ------------------------------------------------------------
--  Enquetes
-- ------------------------------------------------------------
CREATE TABLE safety.investigations (
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
    occurrence_id  uuid        REFERENCES safety.occurrences (id),
    title          text        NOT NULL,
    -- Qui enquete. Une enquete sans responsable nomme n'avance pas.
    investigator   uuid        REFERENCES crew.persons (id),
    investigator_name text,
    opened_on      date        NOT NULL,
    target_on      date,
    closed_on      date,
    status         text        NOT NULL DEFAULT 'OPEN',
    -- La methode suivie : elle dit au lecteur quelle profondeur d'analyse
    -- attendre d'un rapport, et deux methodes ne produisent pas les memes
    -- causes racines.
    method         text,
    findings       text,
    root_cause     text,
    contributing_factors text,

    CONSTRAINT uq_investigation_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_investigation_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'REPORT_DRAFT', 'CLOSED')),
    CONSTRAINT ck_investigation_dates CHECK (closed_on IS NULL OR closed_on >= opened_on),
    -- Une enquete close sans cause racine n'a pas abouti, elle a ete
    -- abandonnee. La contrainte oblige a le dire.
    CONSTRAINT ck_investigation_closed CHECK (
        status <> 'CLOSED' OR (closed_on IS NOT NULL AND root_cause IS NOT NULL))
);

-- ------------------------------------------------------------
--  Gestion du changement — ORO.GEN.200(a)(3)
-- ------------------------------------------------------------
CREATE TABLE safety.changes (
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
    title          text        NOT NULL,
    description    text,
    -- Ce que le changement touche : une flotte, une route, une
    -- organisation, un systeme. Le domaine decide qui doit etre consulte.
    domain         text        NOT NULL,
    raised_on      date        NOT NULL,
    effective_on   date,
    closed_on      date,
    status         text        NOT NULL DEFAULT 'ASSESSING',
    owner_name     text,
    -- L'evaluation de risque du changement, avant et apres mesures.
    initial_index  smallint,
    residual_index smallint,
    mitigation     text,

    CONSTRAINT uq_change_reference UNIQUE (tenant_id, reference),
    CONSTRAINT ck_change_status CHECK (status IN ('ASSESSING', 'APPROVED', 'IMPLEMENTING', 'CLOSED', 'REJECTED')),
    CONSTRAINT ck_change_index CHECK (
        (initial_index IS NULL OR initial_index BETWEEN 1 AND 25)
        AND (residual_index IS NULL OR residual_index BETWEEN 1 AND 25)),
    -- Des mesures qui aggravent le risque ne sont pas des mesures.
    CONSTRAINT ck_change_mitigation CHECK (
        residual_index IS NULL OR initial_index IS NULL OR residual_index <= initial_index)
);

-- ------------------------------------------------------------
--  Indicateurs de performance de securite
-- ------------------------------------------------------------
CREATE TABLE safety.spi_definitions (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    code           text        NOT NULL,
    name           text        NOT NULL,
    unit           text,
    domain         text        NOT NULL,
    target_value   numeric(8,2) NOT NULL,
    -- Le seuil qui declenche une revue de securite. Distinct de la cible :
    -- rater la cible se corrige, franchir l'alerte se convoque.
    alert_value    numeric(8,2),
    -- LOWER : plus c'est bas, mieux c'est (taux d'evenements).
    -- HIGHER : plus c'est haut, mieux c'est (taux de cloture).
    -- Sans cette colonne, 38,5 % pour une cible de 85 % se lirait comme
    -- un succes dans un ecran qui compare betement deux nombres.
    direction      text        NOT NULL,
    -- Le service qui calcule la valeur. Une definition qu'aucun calcul ne
    -- lit est une intention, pas un indicateur.
    computed_by    text,
    sort_order     smallint    NOT NULL DEFAULT 0,

    CONSTRAINT uq_spi_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_spi_direction CHECK (direction IN ('LOWER', 'HIGHER'))
);
