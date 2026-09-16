-- ============================================================
--  V38 — le programme d'audit, les enquetes, les changements
--  et les huit indicateurs de l'annexe A4.
--
--  D'OU. Le magasin TNPSMS du prototype : db.audits (l. 64146),
--  db.findings (64161), db.investigations (64118), db.moc (64200)
--  et SMS.spiDefs (63883).
--
--  TROIS TRADUCTIONS QUI NE SONT PAS LITTERALES.
--
--    score 0 -> NULL. Le prototype note a zero les audits qui n'ont
--      pas encore eu lieu. Zero se lit « tout est non conforme », ce
--      qui est le contraire de « on ne sait pas encore ».
--
--    why[] -> root_cause. La methode de l'enquete est une chaine de
--      « pourquoi » ; sa cause racine est le dernier maillon. C'est la
--      definition de la methode, pas une interpretation. Les maillons
--      intermediaires et les facteurs contributifs sont gardes.
--
--    hazardsIdentified -> mitigation (texte). Le prototype compte les
--      dangers d'un changement et pose un booleen « risque accepte ».
--      On n'en tire pas d'indice chiffre : initial_index et
--      residual_index restent vides, et la phrase dit ce qui est su.
--
--  CE QUI N'EST PAS SEME. Les valeurs des indicateurs. Seules leurs
--  DEFINITIONS entrent ici — nom, cible, seuil d'alerte, sens. La
--  valeur se calcule a la lecture sur les donnees reelles : occurrences,
--  actions en retard, echeances equipage, MEL hors intervalle, ARC. Le
--  prototype, lui, affiche des valeurs figees dans son fichier ; c'est
--  ce qui lui fait annoncer « 4 open audit findings » quand sa propre
--  liste d'audits en compte cinq.
-- ============================================================

INSERT INTO safety.audits
    (id, tenant_id, source_type, source_ref, reference, name, standard,
     scope, auditor, external_audit, planned_on, conducted_on, closed_on, status, score_percent)
VALUES
  (md5('aud:AUD-2026-01')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'AUD-2026-01', 'IS-BAO Stage 2 — internal audit', 'IS-BAO',
   'Full SMS', 'Internal', false, DATE '2026-01-15', DATE '2026-01-15', DATE '2026-01-15',
   'CLOSED', 72),
  (md5('aud:AUD-2026-02')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'AUD-2026-02', 'EASA management system annual review', 'EASA ORO.GEN.200',
   'ORO.GEN.200 / SMS', 'Internal', false, DATE '2026-03-14', DATE '2026-03-14', DATE '2026-03-14',
   'CLOSED', 68),
  (md5('aud:AUD-2026-03')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'AUD-2026-03', 'Safety risk management process audit', 'ICAO Annex 19',
   'Annex 19 component 2', 'Internal', false, DATE '2026-05-15', DATE '2026-05-15', NULL,
   'IN_PROGRESS', 60),
  (md5('aud:AUD-2026-04')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'AUD-2026-04', 'Flight operations line audit programme (LOSA-style)', 'Internal',
   'Line operations', 'Flight Ops', false, DATE '2026-08-20', NULL, NULL,
   'PLANNED', NULL),
  (md5('aud:AUD-2026-05')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'AUD-2026-05', 'CAMO / continuing airworthiness audit', 'Part-CAMO',
   'M.A. Subpart G', 'External', true, DATE '2026-09-10', NULL, NULL,
   'PLANNED', NULL),
  (md5('aud:AUD-2026-06')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'AUD-2026-06', 'IS-BAO Stage 2 certification audit', 'IS-BAO',
   'Full SMS', 'External', true, DATE '2026-11-18', NULL, NULL,
   'PLANNED', NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO safety.audit_findings
    (id, tenant_id, source_type, source_ref, audit_id, reference, level, title, detail,
     raised_on, due_on, closed_on)
VALUES
  (md5('fnd:FND-2026-011')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS',
   md5('aud:AUD-2026-03')::uuid, 'FND-2026-011', 'LEVEL_2',
   'Safety risk assessments are not consistently reviewed at the interval defined in the SMS manual.', 'Domain: ORG · owner Dupont A.',
   DATE '2026-08-30', DATE '2026-08-30', NULL),
  (md5('fnd:FND-2026-012')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS',
   md5('aud:AUD-2026-03')::uuid, 'FND-2026-012', 'LEVEL_2',
   'SMS familiarisation training records are incomplete for three ground operations staff.', 'Domain: TRAINING · owner Zaidi H.',
   DATE '2026-08-15', DATE '2026-08-15', NULL),
  (md5('fnd:FND-2026-013')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS',
   md5('aud:AUD-2026-03')::uuid, 'FND-2026-013', 'OBSERVATION',
   'Dispatch log entries do not consistently record the safety rationale for a delay decision.', 'Domain: OCC · owner Martin J.',
   DATE '2026-09-15', DATE '2026-09-15', NULL),
  (md5('fnd:FND-2026-008')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS',
   md5('aud:AUD-2026-02')::uuid, 'FND-2026-008', 'LEVEL_2',
   'Management of change process is documented but has not been applied to the recent fleet addition.', 'Domain: ORG · owner Gharbi H.',
   DATE '2026-08-05', DATE '2026-08-05', NULL),
  (md5('fnd:FND-2026-009')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS',
   md5('aud:AUD-2026-02')::uuid, 'FND-2026-009', 'OBSERVATION',
   'Fatigue reports are not analysed as a trend at the Safety Review Board.', 'Domain: CREW · owner Dupont A.',
   DATE '2026-06-30', DATE '2026-06-30', DATE '2026-06-30')
ON CONFLICT (id) DO NOTHING;

INSERT INTO safety.investigations
    (id, tenant_id, source_type, source_ref, reference, title, investigator_name,
     opened_on, target_on, closed_on, status, method, findings, root_cause, contributing_factors)
VALUES
  (md5('inv:INV-2026-0003')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'INV-2026-0003', 'Bird strike — TNP101 / TS-NPA at LFPG',
   'Dupont A.', DATE '2026-07-27', DATE '2026-08-26', NULL, 'OPEN',
   'ICAO Doc 9859 — 5 Why with contributing-factor analysis', 'Wildlife information is available from the aerodrome operator but is not routinely requested.', 'The operator has no seasonal wildlife risk assessment for high-activity fields',
   'Environmental — seasonal bird migration · Organisational — briefing content gap'),
  (md5('inv:INV-2026-0001')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'INV-2026-0001', 'Restricted airspace not integrated in the OFP — TNP510',
   'Martin J.', DATE '2026-07-26', DATE '2026-08-25', NULL, 'OPEN',
   'ICAO Doc 9859 — 5 Why with contributing-factor analysis', 'The flight planning checklist predates the current OFP generation workflow.', 'The checklist was last revised before the current flight planning system was introduced',
   'Procedural — checklist not aligned with the current toolset · Timing — late NOTAM publication')
ON CONFLICT (id) DO NOTHING;

INSERT INTO safety.changes
    (id, tenant_id, source_type, source_ref, reference, title, description, domain,
     raised_on, effective_on, closed_on, status, owner_name,
     initial_index, residual_index, mitigation)
VALUES
  (md5('moc:MOC-2026-0002')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'MOC-2026-0002', 'Introduction of the Embraer Lineage 1000E to the AOC',
   'Addition of a second Lineage 1000E requiring crew type qualification, CAMO capability extension and revised MEL.', 'ORG', DATE '2026-06-01', DATE '2026-09-30',
   NULL, 'ASSESSING', 'Gharbi H.',
   NULL, NULL, '7 hazards identified · assessment in progress'),
  (md5('moc:MOC-2026-0001')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'MOC-2026-0001', 'Transfer of flight watch to the in-house OCC platform',
   'Flight following moved from a third-party service to the internal platform, changing the alerting chain.', 'OCC', DATE '2026-03-10', DATE '2026-05-15',
   DATE '2026-05-15', 'CLOSED', 'Martin J.',
   NULL, NULL, '4 hazards identified · residual risk accepted')
ON CONFLICT (id) DO NOTHING;

INSERT INTO safety.spi_definitions
    (id, tenant_id, source_type, source_ref, code, name, unit, domain,
     target_value, alert_value, direction, computed_by, sort_order)
VALUES
  (md5('spi:SPI-01')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-01', 'Occurrence rate', 'per 100 sectors', 'ORG',
   2, 3, 'LOWER', 'SafetyService (occurrences / sectors flown)', 10),
  (md5('spi:SPI-02')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-02', 'High/critical risk occurrences', 'per month', 'ORG',
   1, 3, 'LOWER', 'SafetyService (risk index >= 10)', 20),
  (md5('spi:SPI-03')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-03', 'Reports closed within 30 days', '%', 'ORG',
   85, 70, 'HIGHER', 'SafetyService (occurrence closure)', 30),
  (md5('spi:SPI-04')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-04', 'Overdue corrective actions', 'count', 'ORG',
   0, 3, 'LOWER', 'SafetyService (safety.actions past due)', 40),
  (md5('spi:SPI-05')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-05', 'Crew currency compliance', '%', 'TRAINING',
   100, 95, 'HIGHER', 'CrewDocumentChecker', 50),
  (md5('spi:SPI-06')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-06', 'MEL items beyond interval', 'count', 'TECHLOG',
   0, 1, 'LOWER', 'AircraftService (MEL due status)', 60),
  (md5('spi:SPI-07')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-07', 'Fleet airworthiness compliance', '%', 'CAMO',
   100, 98, 'HIGHER', 'CamoService (ARC verdict + overdue tasks)', 70),
  (md5('spi:SPI-08')::uuid, '00000000-0000-0000-0000-000000000001', 'seed', 'NetPlus RFP annexe A4 — TNPSMS', 'SPI-08', 'Voluntary reports received', 'per month', 'ORG',
   8, 4, 'HIGHER', 'SafetyService (anonymous + voluntary)', 80)
ON CONFLICT (id) DO NOTHING;

-- Les enquetes renvoient a une occurrence par sa reference. Le rattachement
-- se fait apres coup, et en UPDATE plutot qu'en sous-requete dans l'INSERT :
-- une enquete dont l'occurrence n'est pas en base reste orpheline au lieu de
-- faire echouer la migration. Les references du prototype ne sont pas toutes
-- celles de cette base, et c'est normal — ce sont deux jeux de donnees.
UPDATE safety.investigations i SET occurrence_id = o.id
   FROM safety.occurrences o
  WHERE i.id = md5('inv:INV-2026-0003')::uuid AND o.tenant_id = i.tenant_id
    AND o.reference = 'OCC-2026-0113';
UPDATE safety.investigations i SET occurrence_id = o.id
   FROM safety.occurrences o
  WHERE i.id = md5('inv:INV-2026-0001')::uuid AND o.tenant_id = i.tenant_id
    AND o.reference = 'OCC-2026-0111';
