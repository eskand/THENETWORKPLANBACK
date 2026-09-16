-- ============================================================
--  V41 — le signalement, cote declarant.
--
--  POURQUOI. Safety Reports, dans l'annexe A4, n'est pas un ecran de
--  statistiques : c'est le FORMULAIRE par lequel n'importe qui dans
--  la compagnie signale quelque chose — equipage de conduite et de
--  cabine, dispatch, OCC, maintenance, operations sol. Six pages :
--  nouveau signalement, retour d'experience, mes signalements,
--  action requise, bibliotheque REX, formulaires et guidance.
--
--  Ici safety.occurrences portait tout ce qu'il faut pour ANALYSER
--  un evenement — jusqu'aux champs ECCAIRS que l'audit reprochait au
--  prototype de ne pas avoir — mais rien de ce que le declarant
--  ecrit de sa main.
--
--  CE QUI MANQUAIT, ET POURQUOI CHACUN COMPTE.
--
--    report_type — « incident », « danger », « fatigue », « sol »…
--      Dix types, parce que le formulaire n'est pas le meme selon
--      qu'on signale un quasi-abordage ou une fatigue. La categorie
--      ECCAIRS reste a cote : elle sert a exporter a l'autorite,
--      lui sert a choisir les bonnes questions.
--
--    immediate_action — ce qui a ete fait SUR LE MOMENT. C'est la
--      premiere chose que demande un enqueteur, et la derniere dont
--      on se souvient si on ne l'ecrit pas tout de suite.
--
--    reporter_suggestion — ce que le declarant propose. C'est le
--      coeur d'une culture de signalement : la personne qui a vu
--      l'evenement a souvent la meilleure idee de ce qui l'aurait
--      evite, et ne pas lui demander revient a jeter cette idee.
--
--    confidential — distinct d'anonymous, et ce n'est pas un detail.
--      ANONYME : personne ne sait qui a ecrit, y compris le
--      responsable securite ; on ne peut pas rappeler le declarant
--      pour une precision. CONFIDENTIEL : le responsable securite
--      sait, et de-identifie avant de partager. La politique de
--      Just Culture du prototype decrit exactement ce second cas, et
--      une seule colonne booleenne ne peut pas porter les deux.
--
--  LES BROUILLONS. Un signalement s'ecrit souvent en deux fois — on
--  commence a chaud, on finit apres le vol. Sans brouillon, celui
--  qui est interrompu ne revient pas.
-- ============================================================

ALTER TABLE safety.occurrences
    ADD COLUMN report_type         text,
    ADD COLUMN immediate_action    text,
    ADD COLUMN reporter_suggestion text,
    ADD COLUMN confidential        boolean NOT NULL DEFAULT false;

-- Les signalements anonymes deja en base deviennent confidentiels, ce
-- qu'ils etaient de fait : nul ne sait qui les a ecrits, donc nul ne peut
-- les de-identifier. Fait AVANT la contrainte, qui les refuserait sinon.
UPDATE safety.occurrences SET confidential = true WHERE anonymous;

ALTER TABLE safety.occurrences
    ADD CONSTRAINT ck_occurrence_report_type CHECK (report_type IS NULL OR report_type IN (
        'INCIDENT', 'HAZARD', 'NEAR_MISS', 'OBSERVATION', 'FATIGUE',
        'TECHNICAL', 'GROUND', 'DISPATCH', 'SECURITY', 'REX')),
    -- Un signalement anonyme est confidentiel par construction : nul ne
    -- peut le de-identifier puisque nul ne sait qui l'a ecrit. L'inverse
    -- est faux, et c'est la distinction qui compte.
    ADD CONSTRAINT ck_occurrence_confidentiality CHECK (
        NOT anonymous OR confidential);

COMMENT ON COLUMN safety.occurrences.confidential IS
    'Le responsable securite connait le declarant et de-identifie avant partage. '
    'Distinct de anonymous, ou personne ne le connait.';

-- ------------------------------------------------------------
--  Brouillons
-- ------------------------------------------------------------
CREATE TABLE safety.report_drafts (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    -- A qui appartient le brouillon. Un brouillon n'est visible que de
    -- son auteur : ce n'est pas encore un signalement, et le lire avant
    -- qu'il soit soumis reviendrait a lire par-dessus l'epaule.
    author_id      uuid        REFERENCES crew.persons (id),
    author_name    text        NOT NULL,

    report_type    text,
    title          text,
    occurred_at    timestamptz,
    phase_of_flight text,
    station_icao   text,
    flight_no      text,
    registration   text,
    narrative      text,
    immediate_action text,
    reporter_suggestion text,
    anonymous      boolean     NOT NULL DEFAULT false,
    confidential   boolean     NOT NULL DEFAULT false,

    -- Le signalement issu de ce brouillon, une fois soumis. Le brouillon
    -- n'est pas supprime a la soumission : le declarant doit pouvoir
    -- retrouver ce qu'il a envoye, et depuis quel brouillon.
    submitted_occurrence_id uuid REFERENCES safety.occurrences (id),
    submitted_at   timestamptz
);

CREATE INDEX ix_report_drafts_author
    ON safety.report_drafts (tenant_id, author_id)
    WHERE submitted_at IS NULL;
