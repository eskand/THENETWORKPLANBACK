-- ============================================================
--  V44 — le manuel ERP, en base.
--
--  POURQUOI. Le prototype porte tout son plan d'urgence dans son
--  propre code : les cinq niveaux, les trente-neuf evenements, les
--  cent dix actions de cellule, les onze notifications legales, les
--  six modeles de communication, les huit criteres de stand-down et
--  le questionnaire de severite. C'est un manuel, et un manuel se
--  revise — pas en redeployant l'application.
--
--  Ce qui est ici, et pourquoi la : le catalogue d'evenements et le
--  questionnaire sont reglementaires (Annexe 13, Doc 9481, Annexe 12,
--  Annexe 17) et ne varient pas d'un exploitant a l'autre : refdata.
--  Les niveaux, les checklists, les notifications, les modeles et les
--  criteres sont le manuel de CET exploitant : safety, par tenant.
--
--  ET L'ETAT DE LA CRISE. Une activation n'est pas une ligne : c'est
--  l'avion concerne, des cases cochees par quelqu'un a une heure
--  donnee, des notifications faites, des points de situation, et un
--  journal. Le prototype garde tout cela dans localStorage — il le
--  dit lui-meme : « CRISIS LOG NOT BEING STORED ». Ici c'est stocke,
--  et un journal de crise qui n'est pas stocke n'existe pas.
-- ============================================================

-- ------------------------------------------------------------
--  1. Le catalogue reglementaire — refdata, sans tenant
-- ------------------------------------------------------------
CREATE TABLE refdata.erp_event_categories (
    code       text    PRIMARY KEY,
    name       text    NOT NULL,
    sort_order integer NOT NULL DEFAULT 0
);

CREATE TABLE refdata.erp_events (
    code          text     PRIMARY KEY,
    category_code text     NOT NULL REFERENCES refdata.erp_event_categories (code),
    -- Le niveau ou l'evenement se situe AVANT le questionnaire.
    -- L'evaluation ne peut que l'elever : on ne se parle pas d'un
    -- MAYDAY vers le bas.
    base_level    smallint NOT NULL,
    squawk        text,
    label         text     NOT NULL,
    note          text     NOT NULL,
    sort_order    integer  NOT NULL DEFAULT 0,
    CONSTRAINT ck_erp_event_level CHECK (base_level BETWEEN 0 AND 4)
);

CREATE TABLE refdata.erp_questions (
    code       text    PRIMARY KEY,
    question   text    NOT NULL,
    sort_order integer NOT NULL DEFAULT 0
);

CREATE TABLE refdata.erp_question_options (
    question_code text     NOT NULL REFERENCES refdata.erp_questions (code) ON DELETE CASCADE,
    value         text     NOT NULL,
    label         text     NOT NULL,
    -- Le niveau que cette reponse impose. Zero ne veut pas dire
    -- « rien » : il veut dire « n'eleve pas ».
    level         smallint NOT NULL,
    sort_order    integer  NOT NULL DEFAULT 0,
    PRIMARY KEY (question_code, value),
    CONSTRAINT ck_erp_opt_level CHECK (level BETWEEN 0 AND 4)
);

-- ------------------------------------------------------------
--  2. Le manuel de l'exploitant — safety, par tenant
-- ------------------------------------------------------------
CREATE TABLE safety.erp_levels (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    level       smallint NOT NULL,
    name        text     NOT NULL,
    colour      text     NOT NULL,
    description text     NOT NULL,
    -- Ce que l'activation declenche, et qui se leve. Deux phrases que
    -- l'ecran affiche telles quelles : a 3 h du matin on ne resume pas.
    activation  text     NOT NULL,
    stands_up   text     NOT NULL,
    CONSTRAINT uq_erp_level UNIQUE (tenant_id, level),
    CONSTRAINT ck_erp_level_n CHECK (level BETWEEN 0 AND 4)
);

CREATE TABLE safety.erp_checklist_items (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    code       text     NOT NULL,
    -- 0 : la premiere reponse universelle, douze points qu'un officier
    -- de permanence execute sans rien decider. 1 : les cellules.
    phase      smallint NOT NULL,
    dept_code  text     NOT NULL,
    -- Le niveau minimal auquel l'action s'applique. NULL en phase 0 :
    -- la phase 0 s'applique toujours.
    min_level  smallint,
    text       text     NOT NULL,
    sort_order integer  NOT NULL DEFAULT 0,
    CONSTRAINT uq_erp_check UNIQUE (tenant_id, code),
    CONSTRAINT ck_erp_check_phase CHECK (phase IN (0, 1)),
    CONSTRAINT ck_erp_check_level CHECK (min_level IS NULL OR min_level BETWEEN 0 AND 4),
    CONSTRAINT ck_erp_check_p0 CHECK (phase <> 0 OR min_level IS NULL)
);

CREATE TABLE safety.erp_notification_types (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    code         text     NOT NULL,
    min_level    smallint NOT NULL,
    target       text     NOT NULL,
    -- Le delai tel qu'il est ecrit dans le texte : « Immediately »,
    -- « 72 hours », « Before arrival ». Pas un nombre d'heures : un
    -- delai qui ne se compte pas en heures ne doit pas mentir.
    within_label text     NOT NULL,
    basis        text     NOT NULL,
    note         text     NOT NULL,
    sort_order   integer  NOT NULL DEFAULT 0,
    CONSTRAINT uq_erp_notif_type UNIQUE (tenant_id, code),
    CONSTRAINT ck_erp_notif_level CHECK (min_level BETWEEN 0 AND 4)
);

CREATE TABLE safety.erp_templates (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    code       text     NOT NULL,
    min_level  smallint NOT NULL,
    audience   text     NOT NULL,
    title      text     NOT NULL,
    -- Le corps porte ses jetons {FLIGHT}, {REG}, {POB}. Ils sont
    -- remplis a la lecture par le serveur : un modele a moitie rempli
    -- envoye a la presse est pire qu'aucun modele.
    body       text     NOT NULL,
    sort_order integer  NOT NULL DEFAULT 0,
    CONSTRAINT uq_erp_template UNIQUE (tenant_id, code),
    CONSTRAINT ck_erp_template_level CHECK (min_level BETWEEN 0 AND 4)
);

CREATE TABLE safety.erp_standdown_criteria (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    code       text    NOT NULL,
    text       text    NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT uq_erp_sd UNIQUE (tenant_id, code)
);

-- Le titre de poste qui tient la cellule. Le nom, lui, vient du
-- registre du personnel : on n'appelle pas un intitule de poste a
-- trois heures du matin.
ALTER TABLE safety.erp_roles ADD COLUMN lead_role text;

-- ------------------------------------------------------------
--  3. L'etat de la crise
-- ------------------------------------------------------------
ALTER TABLE safety.erp_activations
    ADD COLUMN event_code      text REFERENCES refdata.erp_events (code),
    -- L'avion concerne. Onze champs plutot qu'un JSON : chacun est
    -- lu seul par l'ecran, et un champ vide doit se voir.
    ADD COLUMN flight          text,
    ADD COLUMN registration    text,
    ADD COLUMN aircraft_type   text,
    ADD COLUMN origin          text,
    ADD COLUMN destination     text,
    ADD COLUMN pob             text,
    ADD COLUMN dangerous_goods text,
    ADD COLUMN last_position   text,
    ADD COLUMN squawk          text,
    ADD COLUMN fuel_state      text,
    ADD COLUMN souls           text,
    ADD COLUMN comms_issued    boolean NOT NULL DEFAULT false;

CREATE TABLE safety.erp_activation_checks (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid        NOT NULL REFERENCES platform.tenants (id),
    activation_id uuid        NOT NULL REFERENCES safety.erp_activations (id) ON DELETE CASCADE,
    item_code     text        NOT NULL,
    -- Coche par qui, a quelle heure. Une case cochee sans nom ni heure
    -- ne prouve rien devant une commission d'enquete.
    done_at       timestamptz NOT NULL DEFAULT now(),
    done_by       text        NOT NULL,
    CONSTRAINT uq_erp_check_done UNIQUE (activation_id, item_code)
);

CREATE TABLE safety.erp_activation_notifications (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid        NOT NULL REFERENCES platform.tenants (id),
    activation_id uuid        NOT NULL REFERENCES safety.erp_activations (id) ON DELETE CASCADE,
    type_code     text        NOT NULL,
    made_at       timestamptz NOT NULL DEFAULT now(),
    made_by       text        NOT NULL,
    -- Comment, et a qui : « telephone 02:14 UTC, M. X, BEA ». C'est la
    -- preuve de la notification, pas la notification.
    channel       text,
    reference     text,
    CONSTRAINT uq_erp_notif_made UNIQUE (activation_id, type_code)
);

CREATE TABLE safety.erp_log_entries (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid        NOT NULL REFERENCES platform.tenants (id),
    activation_id uuid        REFERENCES safety.erp_activations (id) ON DELETE CASCADE,
    at            timestamptz NOT NULL DEFAULT now(),
    kind          text        NOT NULL,
    text          text        NOT NULL,
    actor         text,
    level         smallint,
    CONSTRAINT ck_erp_log_level CHECK (level IS NULL OR level BETWEEN 0 AND 4)
);

CREATE INDEX ix_erp_log ON safety.erp_log_entries (tenant_id, activation_id, at DESC);

CREATE TABLE safety.erp_sitreps (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid        NOT NULL REFERENCES platform.tenants (id),
    activation_id uuid        NOT NULL REFERENCES safety.erp_activations (id) ON DELETE CASCADE,
    at            timestamptz NOT NULL DEFAULT now(),
    level         smallint    NOT NULL,
    body          text        NOT NULL,
    author        text        NOT NULL,
    CONSTRAINT ck_erp_sitrep_level CHECK (level BETWEEN 0 AND 4)
);

CREATE INDEX ix_erp_sitreps ON safety.erp_sitreps (tenant_id, activation_id, at DESC);

-- ------------------------------------------------------------
--  4. Le contenu de l'annexe A4
-- ------------------------------------------------------------
-- LEVELS
INSERT INTO safety.erp_levels (tenant_id, source_type, source_ref, level, name, colour, description, activation, stands_up) VALUES
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.LEVELS', 0, 'SAFETY REPORT', '#3fb27f', 'Minor safety event with no emergency dimension.', 'No ERP activation. Occurrence report to the SMS within 24 hours.', 'Nobody stands up. The Safety Manager handles it through the normal register.'),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.LEVELS', 1, 'INCIDENT', '#f5c842', 'Abnormal event without a declared emergency. Enhanced monitoring required.', 'OCC Duty Manager retains the event. Mandatory occurrence report.', 'OCC duty desk, Safety Manager informed.'),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.LEVELS', 2, 'EMERGENCY', '#f08a3c', 'PAN PAN, serious technical failure, medical emergency or minimum fuel. Serious situation without immediate threat to life.', 'ERP activated on the joint decision of the OCC Manager and the Safety Manager. Partial cell.', 'OCC, Flight Operations, Engineering, Ground Operations, Safety.'),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.LEVELS', 3, 'MAJOR EMERGENCY', '#e63946', 'MAYDAY declared, squawk 7700, uncontrolled fire, unlawful interference, fuel below final reserve. Grave and immediate danger to life.', 'ERP activated immediately. Emergency Response Centre opened. Accountable Manager informed without delay.', 'Full Emergency Response Centre. Go-Team on standby. Communications and Humanitarian Assistance alerted.'),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.LEVELS', 4, 'ACCIDENT / CATASTROPHE', '#a32230', 'Accident with confirmed or presumed fatalities or serious injuries, hull loss, or an aircraft missing beyond its endurance.', 'Full crisis organisation. Accountable Manager assumes the role of Crisis Director. Statutory notifications begin immediately.', 'Crisis Management Team, Emergency Response Centre, Go-Team deployed, Family Assistance Centre opened, Special Assistance Team mobilised.');

-- EVENT CATEGORIES
INSERT INTO refdata.erp_event_categories (code, name, sort_order) VALUES
  ('INFLIGHT', 'In-flight emergency', 1),
  ('TECHNICAL', 'Aircraft technical', 2),
  ('SECURITY', 'Security and unlawful interference', 3),
  ('MEDICAL', 'Medical and human', 4),
  ('GROUND', 'Ground and aerodrome', 5),
  ('ENVIRON', 'Environmental and external', 6),
  ('COMPANY', 'Company continuity', 7);

-- EVENT CATALOGUE
INSERT INTO refdata.erp_events (code, category_code, base_level, squawk, label, note, sort_order) VALUES
  ('mayday', 'INFLIGHT', 3, '7700', 'MAYDAY — distress declared', 'Grave and imminent danger. Crew has absolute priority; the operator supports and prepares the ground response.', 1),
  ('panpan', 'INFLIGHT', 2, NULL, 'PAN PAN — urgency declared', 'Serious situation without immediate threat to life. Monitor for deterioration into MAYDAY.', 2),
  ('nordo', 'INFLIGHT', 2, '7600', 'Loss of communication (NORDO)', 'Attempt SATCOM, ACARS, company frequency and relay through ATC or another aircraft. Escalate if it exceeds 30 minutes without radar contact.', 3),
  ('missing', 'INFLIGHT', 4, NULL, 'Aircraft missing / overdue beyond endurance', 'SAR phases apply: INCERFA (uncertainty), ALERFA (alert), DETRESFA (distress) — ICAO Annex 12. Contact the Rescue Coordination Centre.', 4),
  ('divert', 'INFLIGHT', 1, NULL, 'Unplanned diversion', 'Not an emergency in itself. Becomes one if driven by a technical failure, weather below minima or a medical case.', 5),
  ('airprox', 'INFLIGHT', 2, NULL, 'Loss of separation / AIRPROX', 'Preserve recorder data and crew statements. Reportable occurrence.', 6),
  ('turb', 'INFLIGHT', 2, NULL, 'Severe turbulence with injuries', 'Treat as an injury event: medical response at destination, cabin damage assessment, occurrence report.', 7),
  ('decomp', 'INFLIGHT', 3, NULL, 'Rapid or explosive decompression', 'Emergency descent expected. Prepare medical response and engineering assessment at the diversion field.', 8),
  ('ditching', 'INFLIGHT', 4, NULL, 'Ditching or forced landing', 'Alert SAR immediately through the RCC. Position, POB and endurance are the three figures that matter.', 9),
  ('evac', 'INFLIGHT', 3, NULL, 'Emergency evacuation ordered', 'Expect injuries even in a successful evacuation. Aerodrome RFFS and medical services lead on site.', 10),
  ('engfire', 'TECHNICAL', 3, NULL, 'Engine fire or failure', 'Single-engine failure on a twin is an emergency. Engineering to prepare the recovery and inspection plan.', 11),
  ('fire', 'TECHNICAL', 3, NULL, 'Uncontrolled fire or smoke on board', 'The most time-critical case in aviation. Assume the crew will land at the nearest suitable aerodrome.', 12),
  ('fuelemg', 'TECHNICAL', 3, NULL, 'MAYDAY FUEL — below final reserve', 'MINIMUM FUEL is an advisory; MAYDAY FUEL is a distress call. Confirm which was declared.', 13),
  ('minfuel', 'TECHNICAL', 2, NULL, 'MINIMUM FUEL declared', 'Advisory to ATC that any further delay makes landing with less than final reserve likely.', 14),
  ('gear', 'TECHNICAL', 3, NULL, 'Landing gear or configuration malfunction', 'Coordinate RFFS readiness and runway availability with the aerodrome.', 15),
  ('ctrl', 'TECHNICAL', 3, NULL, 'Flight control malfunction', 'Expect a long approach and possible runway blockage. Engineering support to the crew via SATCOM.', 16),
  ('dg', 'TECHNICAL', 3, NULL, 'Dangerous goods incident', 'Identify UN number and class from the NOTOC. Brief RFFS before arrival — IATA DGR and ICAO Doc 9481.', 17),
  ('birdstrike', 'TECHNICAL', 2, NULL, 'Bird or wildlife strike with damage', 'Engineering inspection required before further flight. Report to the aerodrome operator.', 18),
  ('excursion', 'GROUND', 3, NULL, 'Runway excursion or overrun', 'Accident until proven otherwise. Preserve the site and do not move the aircraft without the investigation authority.', 19),
  ('hijack', 'SECURITY', 4, '7500', 'Unlawful interference / hijack', 'National security authorities take primacy. Operator supports; do not broadcast on open channels.', 20),
  ('bomb', 'SECURITY', 3, NULL, 'Bomb threat or suspicious device', 'Assess credibility with the security authority. Least risk bomb location procedure if airborne.', 21),
  ('unruly', 'SECURITY', 2, NULL, 'Serious unruly or violent passenger', 'Restraint, diversion decision and police handover at the arrival aerodrome.', 22),
  ('cyber', 'COMPANY', 2, NULL, 'Cyber attack on operational systems', 'Isolate affected systems, revert to the documented manual fallback, preserve logs for investigation.', 23),
  ('medical', 'MEDICAL', 2, NULL, 'Medical emergency on board', 'Ground-to-air medical advisory service, diversion assessment, receiving hospital and ambulance at the field.', 24),
  ('incap', 'MEDICAL', 3, NULL, 'Flight crew incapacitation', 'Single-pilot operation of a two-crew aircraft. Nearest suitable aerodrome and full ground readiness.', 25),
  ('death', 'MEDICAL', 2, NULL, 'Death on board', 'Do not disturb the deceased beyond what dignity requires. Local police and health authority procedures apply.', 26),
  ('health', 'ENVIRON', 2, NULL, 'Communicable disease on board', 'IHR 2005 and ICAO health-related procedures. Notify the destination health authority before arrival.', 27),
  ('gndcoll', 'GROUND', 3, NULL, 'Ground collision or ramp accident', 'Injuries and aircraft damage assessment. Preserve the scene, photograph before anything is moved.', 28),
  ('gndfire', 'GROUND', 3, NULL, 'Fire on the ground — aircraft or facility', 'Aerodrome RFFS leads. Account for every person on board and on the ramp.', 29),
  ('fuelspill', 'GROUND', 2, NULL, 'Fuel spill or contamination', 'Stop the operation, isolate ignition sources, aerodrome and environmental authority notification.', 30),
  ('gndinjury', 'GROUND', 2, NULL, 'Serious injury to ground personnel', 'Occupational safety reporting in addition to the aviation occurrence report.', 31),
  ('deice', 'GROUND', 2, NULL, 'De-icing or anti-icing failure', 'Holdover time exceeded or fluid not applied as required. Aircraft must not depart contaminated.', 32),
  ('volcanic', 'ENVIRON', 3, NULL, 'Volcanic ash encounter', 'Engine inspection mandatory. VAAC advisories and route closure assessment.', 33),
  ('wx', 'ENVIRON', 1, NULL, 'Severe weather disrupting the network', 'Not an emergency unless an aircraft is threatened. Manage as a disruption with a continuity plan.', 34),
  ('apclosure', 'ENVIRON', 1, NULL, 'Aerodrome closure or unavailability', 'Alternate strategy, passenger care obligations, crew duty implications.', 35),
  ('natural', 'ENVIRON', 2, NULL, 'Natural disaster affecting operations', 'Personnel accountability first, then aircraft and facilities, then network recovery.', 36),
  ('itfail', 'COMPANY', 2, NULL, 'Loss of critical operational systems', 'Revert to the manual dispatch fallback. Flight planning, mass and balance and crew records must remain available.', 37),
  ('occloss', 'COMPANY', 3, NULL, 'Loss of the operations control centre', 'Relocate to the alternate OCC. Continuity of flight watch is the priority.', 38),
  ('staffloss', 'COMPANY', 2, NULL, 'Loss of key personnel or industrial action', 'Verify that every remaining post holder function is covered before continuing operations.', 39);

-- QUESTIONS
INSERT INTO refdata.erp_questions (code, question, sort_order) VALUES
  ('persons', 'Persons on board — what is known?', 1),
  ('aircraft', 'Aircraft condition', 2),
  ('declared', 'What has the crew declared?', 3),
  ('contact', 'Contact and tracking', 4),
  ('fuel', 'Fuel state', 5),
  ('where', 'Where is the aircraft?', 6),
  ('thirdparty', 'Third party or ground consequence', 7);

INSERT INTO refdata.erp_question_options (question_code, value, label, level, sort_order) VALUES
  ('persons', 'ok', 'All accounted for, no injuries reported', 0, 1),
  ('persons', 'minor', 'Minor injuries reported', 2, 2),
  ('persons', 'serious', 'Serious injuries reported', 3, 3),
  ('persons', 'fatal', 'Fatalities confirmed or presumed', 4, 4),
  ('persons', 'unknown', 'Unknown — no reliable information', 3, 5),
  ('aircraft', 'ok', 'Airworthy, situation under control', 0, 1),
  ('aircraft', 'degraded', 'Degraded but controllable', 2, 2),
  ('aircraft', 'severe', 'Severe damage or control difficulty', 3, 3),
  ('aircraft', 'lost', 'Destroyed, or damage beyond economic repair', 4, 4),
  ('aircraft', 'unknown', 'Unknown', 3, 5),
  ('declared', 'none', 'Nothing declared', 0, 1),
  ('declared', 'panpan', 'PAN PAN — urgency', 2, 2),
  ('declared', 'mayday', 'MAYDAY — distress', 3, 3),
  ('declared', 'squawk', 'Emergency squawk observed without voice contact', 3, 4),
  ('contact', 'ok', 'Two-way contact established, position known', 0, 1),
  ('contact', 'partial', 'Intermittent contact or position only', 2, 2),
  ('contact', 'lost', 'No contact, position last known', 3, 3),
  ('contact', 'nothing', 'No contact and no position — aircraft unlocated', 4, 4),
  ('fuel', 'ok', 'Adequate for the planned diversion', 0, 1),
  ('fuel', 'min', 'MINIMUM FUEL declared', 2, 2),
  ('fuel', 'reserve', 'At or below final reserve', 3, 3),
  ('fuel', 'unknown', 'Unknown', 2, 4),
  ('where', 'airborne', 'Airborne, diverting or continuing', 0, 1),
  ('where', 'ground', 'On the ground at an aerodrome', 0, 2),
  ('where', 'offfield', 'On the ground away from an aerodrome', 4, 3),
  ('where', 'water', 'Over water, beyond gliding range of land', 3, 4),
  ('thirdparty', 'none', 'None', 0, 1),
  ('thirdparty', 'property', 'Property damage on the ground', 3, 2),
  ('thirdparty', 'injury', 'Injury to persons not on board', 4, 3);

-- CHECKLISTS: phase 0, then the departmental cells
INSERT INTO safety.erp_checklist_items (tenant_id, source_type, source_ref, code, phase, dept_code, min_level, text, sort_order) VALUES
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-1', 0, 'OCC', NULL, 'Record the exact UTC time the event became known, and from whom.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-2', 0, 'OCC', NULL, 'Identify the aircraft: callsign, registration, type, and confirm persons on board from the load sheet.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-3', 0, 'OCC', NULL, 'Record last known position, level, heading and fuel on board.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-4', 0, 'OCC', NULL, 'Establish or confirm contact with the crew — company frequency, SATCOM, ACARS, or relay via ATC.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-5', 0, 'OCC', NULL, 'Determine what the crew has declared: MAYDAY, PAN PAN, squawk, or nothing yet.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-6', 0, 'OCC', NULL, 'Notify the OCC Manager and the Safety Manager by voice. Do not rely on a message.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-7', 0, 'OCC', NULL, 'Open the crisis log. Every action from here is timestamped in UTC.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-8', 0, 'OCC', NULL, 'Put the affected aircraft under continuous flight watch and assign the rest of the network to another controller.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-9', 0, 'SAF', NULL, 'Freeze and preserve operational data: flight plan, load sheet, tech log, weather, NOTAM, communications.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-10', 0, 'CMD', NULL, 'Assess severity jointly — OCC Manager and Safety Manager — and set the ERP level.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-11', 0, 'COM', NULL, 'Issue no external information whatsoever until the Accountable Manager authorises it.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.PHASE0', 'p0-12', 0, 'OCC', NULL, 'Prepare the operational package for the crisis cell: OFP, alternates, weather, NOTAM, aerodrome data, RFFS category.', 12),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-01', 1, 'OCC', 2, 'Maintain continuous flight watch and log every position report and clearance change.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-02', 1, 'OCC', 2, 'Provide the crew with nearest suitable aerodromes: runway length, RFFS category, approach aids, weather, NOTAM.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-03', 1, 'OCC', 2, 'Coordinate priority handling and diversion with the relevant ATC units.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-04', 1, 'OCC', 2, 'Recompute landing performance for the actual configuration and mass, and pass it to the crew.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-05', 1, 'OCC', 2, 'Confirm the receiving aerodrome can accept the aircraft and has been told what is coming.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-06', 1, 'OCC', 2, 'Hold or reroute other company flights that the event affects, and record why.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-07', 1, 'OCC', 2, 'Preserve the complete operational file: OFP, ATC log, ACARS traffic, weather, NOTAM, load sheet.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-08', 1, 'OCC', 3, 'Establish a dedicated crisis communication channel and stop routine traffic on it.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-09', 1, 'OCC', 3, 'Confirm the alternate OCC position is available should the primary become unusable.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-10', 1, 'OCC', 4, 'Provide the Rescue Coordination Centre with last position, track, endurance and persons on board.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'OCC-11', 1, 'OCC', 4, 'Produce and maintain the single authoritative flight and event chronology for the investigation.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-01', 1, 'CREW', 2, 'Confirm the exact crew complement on board against the published roster.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-02', 1, 'CREW', 2, 'Verify licences, medicals and recency of the operating crew and record the position at the time of the event.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-03', 1, 'CREW', 2, 'Assess duty and flight time — is the crew legal to continue, and for how long.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-04', 1, 'CREW', 2, 'Identify and place a relief crew on standby with a positioning plan.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-05', 1, 'CREW', 3, 'Nominate a single point of contact for the operating crew so they are not called by five departments.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-06', 1, 'CREW', 3, 'Arrange accommodation, transport and food for the crew at the diversion aerodrome.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-07', 1, 'CREW', 3, 'Remove the crew from the roster and protect them from operational pressure to continue.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-08', 1, 'CREW', 3, 'Activate peer support and, where required, professional psychological support for the crew.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-09', 1, 'CREW', 4, 'Notify crew next of kin — through Humanitarian Assistance, never through an operational channel.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-10', 1, 'CREW', 3, 'Collect crew statements while memory is fresh, with the crew informed of their rights and of Just Culture.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CREW-11', 1, 'CREW', 4, 'Withdraw the crew from duty pending the investigation and record the decision.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-01', 1, 'ENG', 2, 'Retrieve the aircraft technical status: open defects, deferred items, MEL, recent work orders.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-02', 1, 'ENG', 2, 'Provide the crew with technical advice on the failure through the OCC channel.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-03', 1, 'ENG', 2, 'Assess whether the aircraft is airworthy for a ferry flight and under what conditions.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-04', 1, 'ENG', 2, 'Alert the line station or contracted maintenance at the arrival aerodrome and brief them.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-05', 1, 'ENG', 2, 'Prepare the recovery plan: parts, tooling, personnel, transport, expected downtime.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-06', 1, 'ENG', 3, 'Impound and preserve the technical log, maintenance records and work packs for the aircraft.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-07', 1, 'ENG', 4, 'Preserve the flight recorders — no power-up, no test, no download without the investigation authority.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-08', 1, 'ENG', 4, 'Quarantine components identified as involved, with tags and chain of custody.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-09', 1, 'ENG', 3, 'Notify the aircraft and engine manufacturers and request technical support.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-10', 1, 'ENG', 3, 'Review the fleet for the same condition and decide whether a fleet inspection is required.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'ENG-11', 1, 'ENG', 4, 'Provide the investigation authority with configuration, modification and service bulletin status.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-01', 1, 'GND', 2, 'Alert the aerodrome operator and confirm rescue and fire fighting readiness for the expected arrival.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-02', 1, 'GND', 2, 'Confirm ambulance, medical and, where required, police attendance at the stand or runway.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-03', 1, 'GND', 2, 'Prepare a remote stand or isolated parking position if the situation requires it.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-04', 1, 'GND', 2, 'Prepare passenger reception away from public view, with staff briefed on what they may and may not say.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-05', 1, 'GND', 3, 'Account for every person on board on arrival — passengers, crew, infants, extra crew.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-06', 1, 'GND', 3, 'Secure and preserve the scene: photograph before anything is moved, restrict access, log everyone who enters.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-07', 1, 'GND', 3, 'Retrieve and secure the load sheet, NOTOC and baggage and cargo manifests.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-08', 1, 'GND', 3, 'Arrange onward care for passengers: accommodation, transport, meals, communication with families.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-09', 1, 'GND', 4, 'Establish a reception centre for arriving relatives, separate from the terminal.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-10', 1, 'GND', 4, 'Coordinate with the aerodrome on runway and stand closure and the recovery of the aircraft.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'GND-11', 1, 'GND', 4, 'Hand over personal effects only through the process agreed with the investigation authority.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-01', 1, 'SAF', 1, 'Classify the occurrence: accident, serious incident or incident, against ICAO Annex 13 definitions.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-02', 1, 'SAF', 1, 'Open the occurrence in the SMS register and link the crisis log to it.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-03', 1, 'SAF', 2, 'Determine the statutory notifications required and start the clocks.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-04', 1, 'SAF', 2, 'Notify the competent authority within 72 hours under Regulation (EU) 376/2014.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-05', 1, 'SAF', 3, 'Notify the investigation authority immediately for an accident or serious incident — Annex 13 and Reg. (EU) 996/2010 Art. 9.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-06', 1, 'SAF', 3, 'Verify that all evidence preservation actions have actually been carried out, and record who confirmed each.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-07', 1, 'SAF', 3, 'Act as the single liaison point for the investigation authority.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-08', 1, 'SAF', 3, 'Protect safety information: separate the investigation record from any disciplinary or commercial process.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-09', 1, 'SAF', 3, 'Brief the Accountable Manager on the safety picture, not the commercial one.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-10', 1, 'SAF', 2, 'Convene the Safety Review Board once the immediate response is over.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SAF-11', 1, 'SAF', 2, 'Open the internal investigation and appoint the investigator in charge.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-01', 1, 'COM', 2, 'Hold all external communication until the Accountable Manager authorises release.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-02', 1, 'COM', 3, 'Issue the first holding statement: what is known, what the operator is doing, nothing speculative.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-03', 1, 'COM', 3, 'Never release names, causes, or numbers of casualties before the authorities do.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-04', 1, 'COM', 3, 'Brief the switchboard and every public-facing employee on exactly what to say and to whom to refer callers.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-05', 1, 'COM', 3, 'Open and staff a dedicated enquiry number, and publish it.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-06', 1, 'COM', 3, 'Monitor media and social media, and correct material factual errors only.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-07', 1, 'COM', 2, 'Inform all staff before they read it in the news — internal communication first.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-08', 1, 'COM', 4, 'Coordinate every statement with the investigation authority; never comment on cause.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-09', 1, 'COM', 4, 'Prepare the Accountable Manager for a media appearance with the agreed factual line.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'COM-10', 1, 'COM', 3, 'Keep a copy of every statement issued, with the time of release.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-01', 1, 'HUM', 3, 'Stand up the Special Assistance Team and confirm who is contactable now.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-02', 1, 'HUM', 3, 'Establish the passenger and crew list as the single authoritative version, and control who may see it.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-03', 1, 'HUM', 4, 'Notify next of kin in person or by voice — never by message, never by media.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-04', 1, 'HUM', 4, 'Assign a trained assistance representative to each affected family.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-05', 1, 'HUM', 4, 'Open the Family Assistance Centre away from the terminal and the media.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-06', 1, 'HUM', 4, 'Arrange travel, accommodation and interpretation for relatives who wish to travel.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-07', 1, 'HUM', 4, 'Provide honest, regular briefings to families before anything is given to the media.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-08', 1, 'HUM', 4, 'Coordinate with the authorities on victim identification and the return of personal effects.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-09', 1, 'HUM', 4, 'Arrange advance payments where required — Montreal Convention 1999 and Reg. (EC) 889/2002.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-10', 1, 'HUM', 4, 'Plan long-term support: anniversaries, the investigation report, ongoing contact.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HUM-11', 1, 'HUM', 4, 'Record every family contact, who made it and what was said.', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SEC-01', 1, 'SEC', 2, 'Confirm whether the event has a security dimension and inform the national security authority if so.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SEC-02', 1, 'SEC', 2, 'Restrict access to the crisis centre and to the operational data associated with the event.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SEC-03', 1, 'SEC', 3, 'For unlawful interference, hand primacy to the state authorities and support only as directed.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SEC-04', 1, 'SEC', 3, 'Control information security: no images, documents or lists leaving the company uncontrolled.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SEC-05', 1, 'SEC', 3, 'Protect company facilities and personnel from intrusion and from press pressure.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'SEC-06', 1, 'SEC', 4, 'Secure the aircraft and the site against unauthorised access and souvenir removal.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-01', 1, 'LEG', 2, 'Notify the insurer and the broker — most policies require immediate notification.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-02', 1, 'LEG', 2, 'Record which communications are legally privileged and keep them separate.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-03', 1, 'LEG', 3, 'Advise on statements so nothing prejudices the investigation or the insurance position.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-04', 1, 'LEG', 3, 'Verify certificates and approvals in force at the time of the event and preserve the evidence.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-05', 1, 'LEG', 3, 'Handle all correspondence with the authority and the investigation body through one channel.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-06', 1, 'LEG', 4, 'Arrange legal representation for the crew, distinct from the company’s own representation.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'LEG-07', 1, 'LEG', 4, 'Set up the mechanism for advance payments and for handling claims.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HR-01', 1, 'HR', 2, 'Account for every employee involved, on board and on the ground.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HR-02', 1, 'HR', 2, 'Brief managers on how to support their teams and what not to speculate about.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HR-03', 1, 'HR', 3, 'Activate peer support and make professional counselling available to all staff, not only to crew.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HR-04', 1, 'HR', 3, 'Manage fatigue in the crisis organisation itself — set shifts and enforce relief.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HR-05', 1, 'HR', 2, 'Protect staff who reported or acted in good faith from any adverse consequence.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'HR-06', 1, 'HR', 3, 'Plan the return to duty of affected staff, individually and without pressure.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'IT-01', 1, 'IT', 2, 'Confirm that flight watch, flight planning and communication systems are available and stable.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'IT-02', 1, 'IT', 2, 'Take a forensic copy of operational system logs covering the event window.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'IT-03', 1, 'IT', 2, 'Extend data retention so nothing relevant is overwritten by a routine purge.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'IT-04', 1, 'IT', 3, 'Stand up the crisis centre technical facilities: displays, telephony, conferencing, recording.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'IT-05', 1, 'IT', 3, 'Verify the alternate OCC is reachable and current.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'IT-06', 1, 'IT', 2, 'Suspend all non-essential system changes and releases for the duration.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-01', 1, 'CMD', 2, 'Confirm the ERP level and that the correct cells have stood up.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-02', 1, 'CMD', 3, 'Assume the role of Crisis Director and make it explicit who holds it.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-03', 1, 'CMD', 3, 'Set the objectives for the first hour and for the first twelve hours, and write them down.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-04', 1, 'CMD', 3, 'Authorise external communication and family notification.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-05', 1, 'CMD', 3, 'Establish a fixed briefing rhythm for the crisis cell and hold to it.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-06', 1, 'CMD', 4, 'Decide on the deployment of the Go-Team and appoint its leader.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-07', 1, 'CMD', 3, 'Take the decisions the departments cannot: fleet grounding, route suspension, service cancellation.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-08', 1, 'CMD', 2, 'Ensure the response itself is being recorded for the post-event review.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.CHECKLISTS', 'CMD-09', 1, 'CMD', 2, 'Declare stand-down only when every criterion is met, and record it.', 9);

-- STATUTORY NOTIFICATIONS
INSERT INTO safety.erp_notification_types (tenant_id, source_type, source_ref, code, min_level, target, within_label, basis, note, sort_order) VALUES
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'aib', 3, 'Accident investigation authority', 'Immediately', 'ICAO Annex 13 §4.1 · Reg. (EU) 996/2010 Art. 9', 'Accident or serious incident. Notification before anything is moved.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'caa', 2, 'Civil aviation authority (State of the Operator)', 'Immediately for L3–L4', 'EASA ORO.GEN.160 · national CAA requirement', 'Occurrence affecting the safety of the operation.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'mor', 1, 'Competent authority — occurrence report', '72 hours', 'Regulation (EU) 376/2014 Art. 4(3)', 'Filed through the SMS. Applies even without ERP activation.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'state', 4, 'State of Occurrence / State of Registry', 'Immediately', 'ICAO Annex 13 §4.1', 'Where the accident occurs outside the State of the Operator.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'rcc', 3, 'Rescue Coordination Centre', 'Immediately', 'ICAO Annex 12', 'Aircraft missing, ditching, forced landing or distress phase declared.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'insurer', 2, 'Insurer and broker', 'Immediately', 'Policy condition', 'Late notification can prejudice cover.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'mfr', 3, 'Aircraft and engine manufacturer', 'As soon as practicable', 'Continued airworthiness support', 'Technical support and fleet implications.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'aerodrome', 2, 'Aerodrome operator and RFFS', 'Before arrival', 'ICAO Annex 14 · aerodrome emergency plan', 'So the ground response is ready when the aircraft is.', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'security', 3, 'National security authority', 'Immediately', 'ICAO Annex 17', 'Unlawful interference, bomb threat or security incident only.', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'health', 2, 'Destination public health authority', 'Before arrival', 'IHR 2005 · ICAO health procedures', 'Communicable disease suspected on board.', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.NOTIFICATIONS', 'camo', 2, 'CAMO and continuing airworthiness authority', 'As soon as practicable', 'Part-CAMO / Part-M', 'Airworthiness implications of the event.', 11);

-- COMMUNICATION TEMPLATES
INSERT INTO safety.erp_templates (tenant_id, source_type, source_ref, code, min_level, audience, title, body, sort_order) VALUES
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.TEMPLATES', 'internal', 2, 'All personnel', 'Internal alert — ERP activated', 'ERP ACTIVATED — LEVEL {LEVEL}

At {TIME} UTC on {DATE} the Emergency Response Plan was activated in relation to flight {FLIGHT} ({REG}).

The Emergency Response Centre is open and the response is being directed from there. Departmental cells have been stood up.

All enquiries from outside the company, including from the media, are to be referred to Communications without exception. No member of staff is to comment on this event on any channel, including personal social media.

You will be kept informed. If you hold information relevant to the response, pass it to your departmental cell lead.

{SM}
Safety Manager', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.TEMPLATES', 'holding', 3, 'Media and public', 'First holding statement', 'THE NETWORK PLAN AIRLINES — STATEMENT
{DATE} {TIME} UTC

The Network Plan Airlines confirms that flight {FLIGHT}, an {TYPE} registered {REG}, operating from {FROM} to {TO}, is the subject of an ongoing emergency response.

The company has activated its Emergency Response Plan. Our immediate concern is the safety and wellbeing of everyone on board.

We are working with the relevant authorities and will provide further factual information as soon as it is confirmed. We will not speculate on the cause.

A dedicated enquiry number has been opened for relatives: {PHONE}

Media enquiries: {MEDIA}', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.TEMPLATES', 'crew', 2, 'Operating crew', 'Message to the operating crew', 'TO THE CREW OF {FLIGHT} / {REG}

The company has activated its Emergency Response Plan and the Emergency Response Centre is open.

Your single point of contact in the company is {CREWLEAD}. Do not accept operational direction from any other source.

You are relieved of any pressure to continue the operation. If you assess that continuing is not safe, that decision will be supported without question.

We are arranging ground support, accommodation and transport. Your families will be contacted by the company through the assistance team, not through operational channels.

Record what you can while it is fresh, but only when you are no longer operating.

{AM}
Accountable Manager', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.TEMPLATES', 'family', 4, 'Relatives enquiry line script', 'Family enquiry line — opening script', 'FAMILY ENQUIRY LINE — SCRIPT

Open with: "You are through to The Network Plan Airlines enquiry line. My name is ___. May I take your name and the name of the person you are calling about?"

DO:
  - Take the caller’s name, relationship, and contact number, and read it back.
  - Tell them honestly what is confirmed and what is not.
  - Tell them when they will next hear from us, and make sure that happens.
  - Pass every call to a named assistance representative.

DO NOT:
  - Confirm or deny that a named person was on board. That is done by a trained representative, in person or by voice, never on a first call.
  - Speculate on cause, casualties or timescales.
  - Say "no comment" or "I am not allowed to say". Say what you can and be honest that you do not know the rest.

Every call is logged: time, caller, what was said, what was promised.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.TEMPLATES', 'authority', 3, 'Investigation authority', 'Notification to the investigation authority', 'NOTIFICATION OF {CLASSIFICATION}
ICAO Annex 13 / Regulation (EU) 996/2010 Article 9

Operator: The Network Plan Airlines — AOC {AOC}
Aircraft type and registration: {TYPE} / {REG}
Flight number: {FLIGHT}
Route: {FROM} to {TO}
Date and time of occurrence: {DATE} {TIME} UTC
Last known position: {POS}
Persons on board: {POB}
Dangerous goods on board: {DG}
Nature of the occurrence: {NATURE}
Present aircraft situation: {SITUATION}

Operator point of contact: {SM}, Safety Manager
Accountable Manager: {AM}

The operator has secured the flight recorders, the technical records and the operational file, and has instructed that nothing be moved or powered up pending your instructions.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.TEMPLATES', 'standdown', 2, 'All personnel', 'Stand-down notice', 'ERP STAND-DOWN

At {TIME} UTC on {DATE} the Emergency Response Plan was stood down in relation to flight {FLIGHT} ({REG}).

The stand-down criteria have been verified: the situation is resolved, all statutory notifications have been made, all evidence has been preserved, and normal operations have been restored.

The event now moves to the safety process. The occurrence remains open in the SMS register and a post-event review will be convened.

Thank you to everyone involved. If this event has affected you, support is available and using it is encouraged.

{AM}
Accountable Manager', 6);

-- STAND-DOWN CRITERIA
INSERT INTO safety.erp_standdown_criteria (tenant_id, source_type, source_ref, code, text, sort_order) VALUES
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd1', 'The emergency is resolved: the aircraft is on the ground and every person on board is accounted for.', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd2', 'All statutory notifications have been made within their time limits and the evidence of each is filed.', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd3', 'All operational and technical data has been preserved and secured for the investigation.', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd4', 'Crew and passengers have been cared for and, where required, families have been contacted.', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd5', 'The occurrence is open in the SMS register with the crisis log attached.', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd6', 'Normal operations have been restored, or a continuity plan is in force and owned.', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd7', 'The post-event review is scheduled with a named chair.', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - ERP.STANDDOWN', 'sd8', 'Staff welfare has been addressed and support has been offered to everyone involved.', 8);

-- THE LEAD ROLE OF EACH CELL
UPDATE safety.erp_roles SET lead_role = 'Accountable Manager' WHERE role_code = 'CMD';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Flight Operations / OCC Manager' WHERE role_code = 'OCC';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Crew Training' WHERE role_code = 'CREW';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Continuing Airworthiness (CAMO)' WHERE role_code = 'ENG';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Ground Operations' WHERE role_code = 'GND';
UPDATE safety.erp_roles SET lead_role = 'Safety Manager' WHERE role_code = 'SAF';
UPDATE safety.erp_roles SET lead_role = 'Accountable Manager' WHERE role_code = 'COM';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Ground Operations' WHERE role_code = 'HUM';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Ground Operations' WHERE role_code = 'SEC';
UPDATE safety.erp_roles SET lead_role = 'Accountable Manager' WHERE role_code = 'LEG';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Crew Training' WHERE role_code = 'HR';
UPDATE safety.erp_roles SET lead_role = 'Post Holder — Flight Operations / OCC Manager' WHERE role_code = 'IT';
