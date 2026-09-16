-- ============================================================
--  V43 — la console de crise.
--
--  POURQUOI. ERP, dans l'annexe A4, n'est pas une liste de roles :
--  c'est une console. Elle a un etat — arme, ou active a un niveau —
--  une organisation de crise a quatorze cellules, une double
--  autorisation d'activation, et un registre d'exercices.
--
--  Ici safety.erp_plans / erp_roles / erp_activations portaient
--  l'ossature. Ce qui manquait est ce qui fait qu'une console de
--  crise est utilisable sous pression :
--
--    LE NIVEAU. De 0 a 4. Le prototype colore tout l'ecran d'apres
--      lui, et pour cause : un niveau 1 se gere depuis l'OCC, un
--      niveau 4 ouvre un centre d'assistance aux familles. Sans
--      niveau, « activation » ne dit pas de quoi on parle.
--
--    LA DOUBLE AUTORISATION. L'ecran l'annonce lui-meme :
--      « activation requires the OCC Manager and the Safety Manager
--      acting together ». Une personne seule ne declenche pas un
--      plan d'urgence — et le nom des deux doit rester au dossier,
--      avec la possibilite d'une derogation du dirigeant responsable
--      et de son motif. Quatre colonnes, pas une case a cocher.
--
--    LES QUATORZE CELLULES. Six roles ne couvrent pas une crise :
--      il manque la direction, l'assistance humanitaire, la securite
--      sureté, le juridique, les ressources humaines, l'informatique.
--      Chaque cellule porte son perimetre, parce qu'a 3 h du matin
--      personne ne se souvient de ce que « LEG » recouvre.
--
--    LES EXERCICES. Un plan qui n'est jamais exerce n'est pas un
--      plan. Le registre garde le type, le scenario, le nombre de
--      participants, les constats et les enseignements — c'est ce
--      qu'un auditeur demande a voir.
-- ============================================================

-- ------------------------------------------------------------
--  Les cellules de crise
-- ------------------------------------------------------------
ALTER TABLE safety.erp_roles
    -- Le perimetre de la cellule, en clair. A 3 h du matin, « LEG »
    -- ne dit rien ; « notification de l'assureur, privilege juridique,
    -- avances, correspondance reglementaire » dit tout.
    ADD COLUMN scope  text,
    -- La couleur de la cellule sur la console. Stockee plutot que
    -- choisie par l'ecran : la console, l'organigramme de crise et le
    -- journal montrent la meme cellule et doivent s'accorder.
    ADD COLUMN colour text;

-- ------------------------------------------------------------
--  L'etat de l'activation
-- ------------------------------------------------------------
ALTER TABLE safety.erp_activations
    ADD COLUMN level              smallint,
    ADD COLUMN event_label        text,
    -- Qui a initie, et qui a concouru. Les deux sont obligatoires
    -- pour une activation reelle : c'est la regle du plan, pas une
    -- preference d'interface.
    ADD COLUMN initiated_by_name  text,
    ADD COLUMN initiated_by_role  text,
    ADD COLUMN concurred_by_name  text,
    ADD COLUMN concurred_by_role  text,
    -- La derogation du dirigeant responsable, quand il active seul.
    -- Elle exige un motif : une derogation sans motif n'est pas une
    -- derogation, c'est un contournement.
    ADD COLUMN override_reason    text,
    ADD COLUMN stood_down_by      text,
    ADD COLUMN sms_occurrence_id  uuid REFERENCES safety.occurrences (id);

UPDATE safety.erp_activations SET level = 1 WHERE level IS NULL;

ALTER TABLE safety.erp_activations
    ALTER COLUMN level SET NOT NULL,
    ADD CONSTRAINT ck_erp_level CHECK (level BETWEEN 0 AND 4),
    -- Une activation REELLE porte les deux noms, ou la derogation et
    -- son motif. La base le refuse plutot que de le rappeler.
    ADD CONSTRAINT ck_erp_authorisation CHECK (
        kind <> 'REAL'
        OR (initiated_by_name IS NOT NULL
            AND (concurred_by_name IS NOT NULL OR override_reason IS NOT NULL)));

-- ------------------------------------------------------------
--  Le registre des exercices
-- ------------------------------------------------------------
CREATE TABLE safety.erp_exercises (
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
    plan_id        uuid        REFERENCES safety.erp_plans (id),
    exercise_type  text        NOT NULL,
    scenario       text        NOT NULL,
    held_on        date        NOT NULL,
    -- Le niveau simule : un exercice de communication n'eprouve pas
    -- la meme organisation qu'un exercice grandeur nature.
    level          smallint,
    participants   integer     NOT NULL DEFAULT 0,
    findings       integer     NOT NULL DEFAULT 0,
    status         text        NOT NULL DEFAULT 'PLANNED',
    -- Ce qu'on en a tire. Un exercice clos sans enseignement n'a pas
    -- ete debriefe, et la contrainte oblige a le dire.
    lessons        text,

    CONSTRAINT uq_erp_exercise UNIQUE (tenant_id, reference),
    CONSTRAINT ck_erp_exercise_status CHECK (status IN ('PLANNED', 'HELD', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_erp_exercise_level CHECK (level IS NULL OR level BETWEEN 0 AND 4),
    CONSTRAINT ck_erp_exercise_closed CHECK (
        status <> 'CLOSED' OR (participants > 0 AND lessons IS NOT NULL))
);

CREATE INDEX ix_erp_exercises ON safety.erp_exercises (tenant_id, held_on DESC);

-- ------------------------------------------------------------
--  Les quatorze cellules de l'annexe A4
-- ------------------------------------------------------------
DELETE FROM safety.erp_roles;

INSERT INTO safety.erp_roles
    (tenant_id, source_type, source_ref, plan_id, role_code, role_title, scope, colour, call_order)
SELECT '00000000-0000-0000-0000-000000000001', 'seed',
       'NetPlus RFP annexe A4 — ERP.DEPARTMENTS',
       p.id, d.code, d.title, d.scope, d.colour, d.ord
  FROM safety.erp_plans p,
       (VALUES
         ('CMD',  'Crisis Direction', 'Direction of the response, external authority, decision of last resort.', '#d4a64a', 1),
         ('OCC',  'OCC / Flight Operations', 'Flight watch, diversion support, network recovery, operational data preservation.', '#4b8fd1', 2),
         ('CREW', 'Crew Control', 'Crew accountability, relief crew, duty limits, crew welfare and support.', '#7c5cd6', 3),
         ('ENG',  'Engineering / CAMO', 'Technical assessment, recovery plan, recorder and maintenance record preservation.', '#3fb27f', 4),
         ('GND',  'Ground Operations / Station', 'Station response, RFFS liaison, passenger reception, ramp scene preservation.', '#f08a3c', 5),
         ('SAF',  'Safety (SMS)', 'Occurrence classification, statutory reporting, investigation liaison, evidence integrity.', '#e63946', 6),
         ('COM',  'Communications & Media', 'Holding statement, media handling, staff communication, social media monitoring.', '#4bc0d1', 7),
         ('HUM',  'Humanitarian Assistance', 'Family assistance centre, next-of-kin notification, Special Assistance Team, welfare.', '#d67cb0', 8),
         ('SEC',  'Security', 'Security authority liaison, facility protection, information security during the event.', '#a32230', 9),
         ('LEG',  'Legal, Insurance & Regulatory', 'Insurer notification, legal privilege, advance payments, regulatory correspondence.', '#8a99b3', 10),
         ('HR',   'Human Resources & Welfare', 'Staff support, peer support programme, fatigue during extended response, duty of care.', '#c9a227', 11),
         ('IT',   'IT & Systems', 'System availability, data preservation, alternate OCC readiness, communications tools.', '#5d6e8a', 12)
       ) AS d(code, title, scope, colour, ord)
 WHERE p.tenant_id = '00000000-0000-0000-0000-000000000001';

-- ------------------------------------------------------------
--  Le registre d'exercices de l'annexe A4
-- ------------------------------------------------------------
INSERT INTO safety.erp_exercises
    (id, tenant_id, source_type, source_ref, reference, plan_id, exercise_type, scenario,
     held_on, level, participants, findings, status, lessons)
SELECT md5('drl:' || d.ref)::uuid, '00000000-0000-0000-0000-000000000001', 'seed',
       'NetPlus RFP annexe A4 — ERP drills',
       d.ref, p.id, d.kind, d.scenario, d.held::date, d.lvl, d.people, d.finds, d.status, d.lessons
  FROM safety.erp_plans p,
       (VALUES
         ('DRL-2026-01', 'Full-scale exercise',
          'Accident on landing at DTTA with casualties, family assistance centre opened',
          '2026-01-22', 4, 31, 7, 'CLOSED',
          'Passenger list reconciliation took 2 h 10 min against a 60-minute target. Load sheet retrieval process revised.'),
         ('DRL-2026-02', 'Communications exercise',
          'Media response to an unconfirmed report of an incident',
          '2026-03-05', 2, 6, 2, 'CLOSED',
          'Holding statement issued within 38 minutes. Social media monitoring handed to a named deputy.'),
         ('DRL-2026-03', 'Table-top exercise',
          'Engine fire and diversion — TS-NPA on a European sector',
          '2026-06-18', 3, 14, 3, 'CLOSED',
          'Diversion aerodrome handling contacts were out of date for two of the four alternates.'),
         ('DRL-2026-04', 'Full-scale exercise',
          'Unlawful interference with a diversion to an unfamiliar aerodrome',
          '2026-09-24', 4, 0, 0, 'PLANNED', NULL)
       ) AS d(ref, kind, scenario, held, lvl, people, finds, status, lessons)
 WHERE p.tenant_id = '00000000-0000-0000-0000-000000000001'
ON CONFLICT (id) DO NOTHING;
