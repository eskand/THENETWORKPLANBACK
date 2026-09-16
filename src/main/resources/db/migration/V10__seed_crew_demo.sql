-- ============================================================
--  V10 — jeu de données Crew (qualifications, service, roster,
--  formation), déterministe et dérivé des lignes déjà semées
--  en V8 : aucune personne, aucun vol n'est inventé ici.
--
--  Les identifiants sont calculés (md5 -> uuid) donc stables
--  d'une reconstruction à l'autre. Toutes les lignes portent
--  source_type = 'seed' : elles sont reconnaissables en base.
-- ============================================================

-- ------------------------------------------------------------
--  1. Catalogue de formation (Part-ORO.FC / Part-CC)
-- ------------------------------------------------------------
INSERT INTO crew.training_courses (id, tenant_id, code, title, category, validity_months, mandatory, authority_ref, source_type, source_ref) VALUES
  (md5('course-OPC')::uuid,  '00000000-0000-0000-0000-000000000001', 'OPC',  'Operator Proficiency Check',        'RECURRENT',       6,  true,  'ORO.FC.230', 'seed', 'Training manual'),
  (md5('course-LPC')::uuid,  '00000000-0000-0000-0000-000000000001', 'LPC',  'Licence Proficiency Check',         'RECURRENT',       12, true,  'FCL.625',    'seed', 'Training manual'),
  (md5('course-CRM')::uuid,  '00000000-0000-0000-0000-000000000001', 'CRM',  'Crew Resource Management',          'CRM',             12, true,  'ORO.FC.115', 'seed', 'Training manual'),
  (md5('course-DGR')::uuid,  '00000000-0000-0000-0000-000000000001', 'DGR',  'Dangerous Goods, category 10',      'DANGEROUS_GOODS', 24, true,  'ICAO DOC 9284', 'seed', 'Training manual'),
  (md5('course-SEP')::uuid,  '00000000-0000-0000-0000-000000000001', 'SEP',  'Safety and Emergency Procedures',   'SEP',             12, true,  'ORO.CC.140', 'seed', 'Training manual'),
  (md5('course-LVO')::uuid,  '00000000-0000-0000-0000-000000000001', 'LVO',  'Low Visibility Operations',         'SIMULATOR',       12, false, 'SPA.LVO',    'seed', 'Training manual'),
  (md5('course-SEC')::uuid,  '00000000-0000-0000-0000-000000000001', 'SEC',  'Aviation Security',                 'SECURITY',        36, true,  'Reg. 300/2008', 'seed', 'Training manual'),
  (md5('course-LINE')::uuid, '00000000-0000-0000-0000-000000000001', 'LINE', 'Line training and line check',      'LINE_TRAINING',   12, true,  'ORO.FC.145', 'seed', 'Training manual');

-- ------------------------------------------------------------
--  2. Qualifications de type
--     Chaque pilote est qualifié sur le type de la flotte que
--     lui attribue le reste modulo de son matricule : la
--     répartition est arbitraire mais fixe et vérifiable.
-- ------------------------------------------------------------
INSERT INTO crew.qualifications (id, tenant_id, person_id, aircraft_type_id, kind, level, valid_from, valid_to, reference, source_type, source_ref)
SELECT md5('qual-tr-' || p.staff_no)::uuid,
       p.tenant_id,
       p.id,
       t.id,
       'TYPE_RATING',
       CASE WHEN p.main_role = 'CAPTAIN' THEN 'PIC' ELSE 'SIC' END,
       (date_trunc('day', now()) - interval '400 days')::date,
       p.licence_expiry,
       'Licence endorsement',
       'seed',
       'Crew file'
FROM crew.persons p
JOIN LATERAL (
    SELECT id, row_number() OVER (ORDER BY icao_type) AS rn FROM refdata.aircraft_types
) t ON t.rn = 1 + (abs(('x' || substr(md5(p.staff_no), 1, 8))::bit(32)::int) % 9)
WHERE p.main_role IN ('CAPTAIN', 'FIRST_OFFICER');

-- Un second type pour un pilote sur trois : la polyvalence réelle de la flotte.
INSERT INTO crew.qualifications (id, tenant_id, person_id, aircraft_type_id, kind, level, valid_from, valid_to, reference, source_type, source_ref)
SELECT md5('qual-tr2-' || p.staff_no)::uuid,
       p.tenant_id, p.id, t.id, 'TYPE_RATING',
       CASE WHEN p.main_role = 'CAPTAIN' THEN 'PIC' ELSE 'SIC' END,
       (date_trunc('day', now()) - interval '220 days')::date,
       p.licence_expiry,
       'Licence endorsement',
       'seed', 'Crew file'
FROM crew.persons p
JOIN LATERAL (
    SELECT id, row_number() OVER (ORDER BY icao_type DESC) AS rn FROM refdata.aircraft_types
) t ON t.rn = 1 + (abs(('x' || substr(md5('b' || p.staff_no), 1, 8))::bit(32)::int) % 9)
WHERE p.main_role IN ('CAPTAIN', 'FIRST_OFFICER')
  AND abs(('x' || substr(md5(p.staff_no), 1, 8))::bit(32)::int) % 3 = 0
ON CONFLICT ON CONSTRAINT uq_qualification DO NOTHING;

-- Qualifications non liées à un type : CRM, marchandises dangereuses, SEP,
-- LVO pour les commandants de bord seulement.
INSERT INTO crew.qualifications (id, tenant_id, person_id, aircraft_type_id, kind, level, valid_from, valid_to, reference, source_type, source_ref)
SELECT md5('qual-' || k.kind || '-' || p.staff_no)::uuid,
       p.tenant_id, p.id, NULL, k.kind, NULL,
       (p.training_expiry - interval '12 months')::date,
       p.training_expiry,
       'Training record',
       'seed', 'Crew file'
FROM crew.persons p
CROSS JOIN (VALUES ('CRM'), ('DANGEROUS_GOODS'), ('SEP')) AS k(kind);

INSERT INTO crew.qualifications (id, tenant_id, person_id, aircraft_type_id, kind, level, valid_from, valid_to, reference, source_type, source_ref)
SELECT md5('qual-LVO-' || p.staff_no)::uuid,
       p.tenant_id, p.id, NULL, 'LVO', NULL,
       (date_trunc('day', now()) - interval '200 days')::date,
       (date_trunc('day', now()) + interval '165 days')::date,
       'CAT II/III authorisation',
       'seed', 'Crew file'
FROM crew.persons p
WHERE p.main_role = 'CAPTAIN';

-- ------------------------------------------------------------
--  3. Absences en cours et à venir
-- ------------------------------------------------------------
INSERT INTO crew.absences (id, tenant_id, person_id, kind, starts_on, ends_on, reason, source_type, source_ref)
SELECT md5('abs-' || p.staff_no)::uuid,
       p.tenant_id, p.id,
       CASE abs(('x' || substr(md5('a' || p.staff_no), 1, 8))::bit(32)::int) % 3
            WHEN 0 THEN 'LEAVE' WHEN 1 THEN 'SICK' ELSE 'TRAINING' END,
       (date_trunc('day', now()) + ((abs(('x' || substr(md5('s' || p.staff_no), 1, 8))::bit(32)::int) % 20) - 4) * interval '1 day')::date,
       (date_trunc('day', now()) + ((abs(('x' || substr(md5('s' || p.staff_no), 1, 8))::bit(32)::int) % 20) - 4 + 3) * interval '1 day')::date,
       'Planned in the crew file',
       'seed', 'Crew file'
FROM crew.persons p
WHERE abs(('x' || substr(md5('a' || p.staff_no), 1, 8))::bit(32)::int) % 7 = 0;

-- ------------------------------------------------------------
--  4. Périodes de service
--     4a. Les vacations du jour : elles existent déjà comme
--         affectations de vol (V8). On ne les réinvente pas,
--         on les reprend telles quelles.
-- ------------------------------------------------------------
INSERT INTO crew.duty_periods (id, tenant_id, person_id, leg_id, kind, report_at, off_duty_at, block_minutes, sectors, remark, source_type, source_ref)
SELECT md5('duty-leg-' || a.id::text)::uuid,
       a.tenant_id,
       a.person_id,
       a.leg_id,
       'FLIGHT_DUTY',
       a.duty_start,
       a.duty_end,
       GREATEST(0, (EXTRACT(EPOCH FROM (l.sta - l.std)) / 60)::int),
       1,
       'Duty derived from the assignment of the day',
       'seed', 'Roster'
FROM crew.leg_assignments a
JOIN ops.legs l ON l.id = a.leg_id
WHERE a.duty_start IS NOT NULL AND a.duty_end IS NOT NULL;

--     4b. Les 28 jours écoulés : ce que les compteurs FTL
--         additionnent. Le motif est calculé à partir du
--         matricule et du jour, donc rejouable à l'identique.
INSERT INTO crew.duty_periods (id, tenant_id, person_id, leg_id, kind, report_at, off_duty_at, block_minutes, sectors, remark, source_type, source_ref)
SELECT md5('duty-hist-' || p.staff_no || '-' || d.offset_days::text)::uuid,
       p.tenant_id,
       p.id,
       NULL,
       CASE WHEN r.r < 5 THEN 'FLIGHT_DUTY'
            WHEN r.r < 7 THEN 'STANDBY'
            WHEN r.r = 7 THEN 'TRAINING'
            ELSE 'OFF' END,
       date_trunc('day', now()) - (d.offset_days * interval '1 day') + interval '6 hours',
       date_trunc('day', now()) - (d.offset_days * interval '1 day')
           + CASE WHEN r.r < 5 THEN interval '17 hours'
                  WHEN r.r < 7 THEN interval '18 hours'
                  WHEN r.r = 7 THEN interval '14 hours'
                  ELSE interval '23 hours 59 minutes' END,
       CASE WHEN r.r < 5 THEN 240 + (r.r * 45) ELSE NULL END,
       CASE WHEN r.r < 5 THEN 2 ELSE 0 END,
       NULL,
       'seed', 'Roster'
FROM crew.persons p
CROSS JOIN generate_series(1, 28) AS d(offset_days)
CROSS JOIN LATERAL (
    SELECT abs(('x' || substr(md5(p.staff_no || '-' || d.offset_days::text), 1, 8))::bit(32)::int) % 10 AS r
) r;

-- ------------------------------------------------------------
--  5. Roster : une version publiée (semaine en cours) et une
--     version brouillon (semaine suivante).
-- ------------------------------------------------------------
INSERT INTO crew.roster_versions (id, tenant_id, label, period_start, period_end, status, published_at, published_by, source_type, source_ref) VALUES
  (md5('roster-published')::uuid, '00000000-0000-0000-0000-000000000001', 'Published roster',
   (date_trunc('week', now()))::date, (date_trunc('week', now()) + interval '6 days')::date,
   'PUBLISHED', date_trunc('week', now()) + interval '2 days', 'd007ca51-8be0-5abf-b41a-0f13a4ee1625', 'seed', 'Crew planning'),
  (md5('roster-draft')::uuid, '00000000-0000-0000-0000-000000000001', 'Next week draft',
   (date_trunc('week', now()) + interval '7 days')::date, (date_trunc('week', now()) + interval '13 days')::date,
   'DRAFT', NULL, NULL, 'seed', 'Crew planning');

-- Les cases de la version publiée sont les périodes de service réelles.
INSERT INTO crew.roster_entries (id, tenant_id, roster_version_id, person_id, duty_date, code, duty_period_id, leg_id, remark, source_type, source_ref)
SELECT DISTINCT ON (dp.person_id, (dp.report_at AT TIME ZONE 'UTC')::date, dp.kind)
       md5('rost-' || dp.id::text)::uuid,
       dp.tenant_id,
       md5('roster-published')::uuid,
       dp.person_id,
       (dp.report_at AT TIME ZONE 'UTC')::date,
       CASE dp.kind WHEN 'FLIGHT_DUTY' THEN 'FLT' WHEN 'STANDBY' THEN 'SBY'
                    WHEN 'TRAINING' THEN 'TRG' WHEN 'POSITIONING' THEN 'POS'
                    WHEN 'OFFICE' THEN 'OFFICE' ELSE 'OFF' END,
       dp.id,
       dp.leg_id,
       NULL,
       'seed', 'Crew planning'
FROM crew.duty_periods dp
WHERE (dp.report_at AT TIME ZONE 'UTC')::date
      BETWEEN (date_trunc('week', now()))::date AND (date_trunc('week', now()) + interval '6 days')::date
ORDER BY dp.person_id, (dp.report_at AT TIME ZONE 'UTC')::date, dp.kind, dp.report_at;

-- Le brouillon de la semaine suivante n'a pas encore de vacation :
-- il ne porte que les indisponibilités connues et les jours de repos.
INSERT INTO crew.roster_entries (id, tenant_id, roster_version_id, person_id, duty_date, code, duty_period_id, leg_id, remark, source_type, source_ref)
SELECT md5('rost-draft-' || p.staff_no || '-' || d.offset_days::text)::uuid,
       p.tenant_id,
       md5('roster-draft')::uuid,
       p.id,
       (date_trunc('week', now()) + ((7 + d.offset_days) * interval '1 day'))::date,
       CASE WHEN abs(('x' || substr(md5('n' || p.staff_no || d.offset_days::text), 1, 8))::bit(32)::int) % 10 < 6
            THEN 'RES' ELSE 'OFF' END,
       NULL, NULL, 'Draft, not published',
       'seed', 'Crew planning'
FROM crew.persons p
CROSS JOIN generate_series(0, 6) AS d(offset_days);

-- ------------------------------------------------------------
--  6. Formation : sessions à venir, inscriptions, dossiers.
--     valid_to est écrit à l'enregistrement, à partir de la
--     validité du cours ; il n'est jamais recalculé à l'écran.
-- ------------------------------------------------------------
INSERT INTO crew.training_sessions (id, tenant_id, course_id, starts_at, ends_at, location, capacity, instructor_id, status, source_type, source_ref)
SELECT md5('sess-' || c.code || '-' || s.n::text)::uuid,
       c.tenant_id,
       c.id,
       date_trunc('day', now()) + ((s.n * 9) * interval '1 day') + interval '8 hours',
       date_trunc('day', now()) + ((s.n * 9) * interval '1 day') + interval '16 hours',
       CASE WHEN c.category = 'SIMULATOR' THEN 'CAE Amsterdam' ELSE 'TNP Training Centre, Tunis' END,
       12,
       (SELECT id FROM crew.persons WHERE staff_no = 'CPT001'),
       'PLANNED',
       'seed', 'Training plan'
FROM crew.training_courses c
CROSS JOIN generate_series(1, 2) AS s(n);

INSERT INTO crew.training_enrolments (id, tenant_id, session_id, person_id, status, score, source_type, source_ref)
SELECT md5('enr-' || ts.id::text || p.staff_no)::uuid,
       p.tenant_id, ts.id, p.id, 'BOOKED', NULL, 'seed', 'Training plan'
FROM crew.training_sessions ts
JOIN crew.persons p
  ON abs(('x' || substr(md5(ts.id::text || p.staff_no), 1, 8))::bit(32)::int) % 11 = 0
ON CONFLICT ON CONSTRAINT uq_enrolment DO NOTHING;

-- Dossier de formation : un enregistrement par personne et par cours
-- obligatoire, daté dans le passé, dont la validité découle du cours.
INSERT INTO crew.training_records (id, tenant_id, person_id, course_id, session_id, completed_on, valid_to, score, instructor_id, reference, source_type, source_ref)
SELECT md5('trec-' || p.staff_no || '-' || c.code)::uuid,
       p.tenant_id,
       p.id,
       c.id,
       NULL,
       completed.on_date,
       (completed.on_date + (c.validity_months * interval '1 month'))::date,
       88.0 + (abs(('x' || substr(md5(p.staff_no || c.code), 1, 8))::bit(32)::int) % 12),
       (SELECT id FROM crew.persons WHERE staff_no = 'CPT001'),
       'TR-' || upper(substr(md5(p.staff_no || c.code), 1, 8)),
       'seed', 'Training file'
FROM crew.persons p
CROSS JOIN crew.training_courses c
CROSS JOIN LATERAL (
    SELECT (date_trunc('day', now())
            - ((abs(('x' || substr(md5(p.staff_no || c.code), 1, 8))::bit(32)::int) % 330) * interval '1 day'))::date AS on_date
) completed
WHERE c.mandatory = true
  AND (p.main_role IN ('CAPTAIN', 'FIRST_OFFICER') OR c.code IN ('CRM', 'SEP', 'DGR', 'SEC'));
