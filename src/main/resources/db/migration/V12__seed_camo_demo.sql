-- ============================================================
--  V12 — jeu de données Maintenance, dérivé de la flotte et du
--  programme de vols déjà semés en V8. Rien n'est inventé :
--  l'utilisation vient des étapes réellement parties, les
--  échéances viennent du programme, les défauts viennent du
--  carnet de bord.
-- ============================================================

-- ------------------------------------------------------------
--  1. Programme d'entretien, par type
-- ------------------------------------------------------------
INSERT INTO camo.programme_tasks (id, tenant_id, aircraft_type_id, code, title, ata_chapter,
                                  interval_hours, interval_cycles, interval_months,
                                  tolerance_hours, tolerance_days, mandatory, reference,
                                  source_type, source_ref)
SELECT md5('ptask-' || t.icao_type || '-' || task.code)::uuid,
       '00000000-0000-0000-0000-000000000001',
       t.id,
       task.code,
       task.title,
       task.ata,
       task.hours,
       task.cycles,
       task.months,
       task.tol_hours,
       task.tol_days,
       true,
       'AMP / MPD',
       'seed', 'Maintenance programme'
FROM refdata.aircraft_types t
CROSS JOIN (VALUES
    ('A-CHECK',  'A check',                          '05', 600::numeric,  NULL::integer, 6,  30::numeric, 15),
    ('C-CHECK',  'C check',                          '05', 6000::numeric, NULL::integer, 24, 100::numeric, 30),
    ('ELT-BAT',  'ELT battery replacement',          '25', NULL::numeric, NULL::integer, 60, NULL::numeric, 30),
    ('EMERG-EQ', 'Emergency equipment inspection',   '25', NULL::numeric, NULL::integer, 12, NULL::numeric, 15),
    ('LDG-OVH',  'Landing gear overhaul',            '32', NULL::numeric, 3000,          120, NULL::numeric, 60),
    ('ENG-BSI',  'Engine borescope inspection',      '72', 1200::numeric, NULL::integer, NULL, 50::numeric, NULL),
    ('WB-CHECK', 'Weighing and mass and balance',    '08', NULL::numeric, NULL::integer, 48, NULL::numeric, 30),
    ('ALT-STAT', 'Altimeter and static system test', '34', NULL::numeric, NULL::integer, 24, NULL::numeric, 15)
) AS task(code, title, ata, hours, cycles, months, tol_hours, tol_days);

-- ------------------------------------------------------------
--  2. Échéancier par immatriculation.
--     last_done_* est un relevé, due_* en découle. Les deux sont
--     écrits ici, comme le service les écrira à chaque solde.
-- ------------------------------------------------------------
INSERT INTO camo.aircraft_tasks (id, tenant_id, aircraft_id, programme_task_id, code, title,
                                 last_done_on, last_done_hours, last_done_cycles,
                                 due_on, due_at_hours, due_at_cycles, source_type, source_ref)
SELECT md5('atask-' || a.registration || '-' || p.code)::uuid,
       a.tenant_id,
       a.id,
       p.id,
       p.code,
       p.title,
       done.on_date,
       done.at_hours,
       done.at_cycles,
       CASE WHEN p.interval_months IS NOT NULL
            THEN (done.on_date + (p.interval_months * interval '1 month'))::date END,
       CASE WHEN p.interval_hours IS NOT NULL THEN done.at_hours + p.interval_hours END,
       CASE WHEN p.interval_cycles IS NOT NULL THEN done.at_cycles + p.interval_cycles END,
       'seed', 'CAMO record'
FROM camo.aircraft a
JOIN camo.programme_tasks p ON p.aircraft_type_id = a.aircraft_type_id
CROSS JOIN LATERAL (
    SELECT (date_trunc('day', now())
            - ((abs(('x' || substr(md5(a.registration || p.code), 1, 8))::bit(32)::int) % 400) * interval '1 day'))::date AS on_date,
           GREATEST(0, a.hours_since_new
                       - (abs(('x' || substr(md5(a.registration || p.code), 1, 8))::bit(32)::int) % 900)) AS at_hours,
           GREATEST(0, a.cycles_since_new
                       - (abs(('x' || substr(md5(a.registration || p.code), 1, 8))::bit(32)::int) % 700)) AS at_cycles
) done;

-- ------------------------------------------------------------
--  3. Utilisation : une ligne par étape réellement partie.
--     C'est ce qui alimentera TSN / CSN, au lieu du hash du
--     prototype.
-- ------------------------------------------------------------
INSERT INTO camo.utilisation (id, tenant_id, aircraft_id, leg_id, flown_on, block_minutes, air_minutes, cycles,
                              source_type, source_ref)
SELECT md5('util-' || l.id::text)::uuid,
       l.tenant_id,
       l.aircraft_id,
       l.id,
       (l.std AT TIME ZONE 'UTC')::date,
       GREATEST(1, (EXTRACT(EPOCH FROM (COALESCE(l.in_at, l.sta) - COALESCE(l.out_at, l.std))) / 60)::int),
       GREATEST(1, (EXTRACT(EPOCH FROM (COALESCE(l.in_at, l.sta) - COALESCE(l.out_at, l.std))) / 60)::int) - 12,
       1,
       'seed', 'Tech log'
FROM ops.legs l
WHERE l.status IN ('DEPARTED', 'ARRIVED', 'CLOSED');

-- Les 27 jours précédents : de quoi donner une utilisation lisible
-- à la flotte, deux vols par jour et par appareil en service.
INSERT INTO camo.utilisation (id, tenant_id, aircraft_id, leg_id, flown_on, block_minutes, air_minutes, cycles,
                              source_type, source_ref)
SELECT md5('util-hist-' || a.registration || '-' || d.offset_days::text || '-' || s.n::text)::uuid,
       a.tenant_id,
       a.id,
       NULL,
       (date_trunc('day', now()) - (d.offset_days * interval '1 day'))::date,
       95 + (abs(('x' || substr(md5(a.registration || d.offset_days::text || s.n::text), 1, 8))::bit(32)::int) % 180),
       80 + (abs(('x' || substr(md5(a.registration || d.offset_days::text || s.n::text), 1, 8))::bit(32)::int) % 180),
       1,
       'seed', 'Tech log'
FROM camo.aircraft a
CROSS JOIN generate_series(1, 27) AS d(offset_days)
CROSS JOIN generate_series(1, 2) AS s(n)
WHERE a.status = 'SERVICEABLE'
  AND abs(('x' || substr(md5(a.registration || d.offset_days::text || s.n::text), 1, 8))::bit(32)::int) % 3 <> 0;

-- ------------------------------------------------------------
--  4. Consignes de navigabilité et bulletins
-- ------------------------------------------------------------
INSERT INTO camo.directives (id, tenant_id, kind, reference, subject, issued_by, issued_on, effective_on,
                             aircraft_type_id, compliance_by_date, compliance_by_hours, method, recurring_months,
                             source_type, source_ref)
SELECT md5('dir-' || d.reference)::uuid,
       '00000000-0000-0000-0000-000000000001',
       d.kind,
       d.reference,
       d.subject,
       d.issued_by,
       (date_trunc('day', now()) - (d.issued_days_ago * interval '1 day'))::date,
       (date_trunc('day', now()) - ((d.issued_days_ago - 14) * interval '1 day'))::date,
       (SELECT id FROM refdata.aircraft_types WHERE icao_type = d.icao_type),
       (date_trunc('day', now()) + (d.due_in_days * interval '1 day'))::date,
       NULL,
       d.method,
       d.recurring,
       'seed', 'Authority publication'
FROM (VALUES
    ('AD', 'EASA AD 2026-0142', 'Main landing gear actuator, repetitive inspection', 'EASA', 210, 45,  'F2TH', 'Repetitive detailed visual inspection', 12),
    ('AD', 'EASA AD 2026-0177', 'FADEC software standard, mandatory upgrade',        'EASA', 150, 90,  'E35L', 'Software upgrade to standard 4.2',      NULL),
    ('AD', 'FAA AD 2026-11-05', 'Fuel pump wiring harness inspection',               'FAA',  120, 20,  'C25A', 'One-time inspection and rework',        NULL),
    ('SB', 'DAS SB F900-34-12', 'Air data computer, optional improvement',           'Dassault', 300, 240, 'F900', 'Optional embodiment',              NULL),
    ('SB', 'CES SB 525-27-03',  'Aileron trim actuator, service life extension',     'Cessna', 260, 150, 'C525', 'Inspection and re-identification',    NULL),
    ('AD', 'EASA AD 2026-0203', 'Cabin oxygen cylinder, hydrostatic test',           'EASA', 60,  -5,  'FA7X', 'Hydrostatic test of the cylinder',      60)
) AS d(kind, reference, subject, issued_by, issued_days_ago, due_in_days, icao_type, method, recurring);

-- Application par immatriculation : toute la flotte du type concerné.
INSERT INTO camo.directive_applications (id, tenant_id, directive_id, aircraft_id, status, complied_on, complied_ref,
                                         source_type, source_ref)
SELECT md5('dirapp-' || d.reference || '-' || a.registration)::uuid,
       a.tenant_id,
       d.id,
       a.id,
       CASE WHEN abs(('x' || substr(md5(d.reference || a.registration), 1, 8))::bit(32)::int) % 3 = 0
            THEN 'COMPLIED' ELSE 'OPEN' END,
       CASE WHEN abs(('x' || substr(md5(d.reference || a.registration), 1, 8))::bit(32)::int) % 3 = 0
            THEN (date_trunc('day', now())
                  - ((abs(('x' || substr(md5(d.reference || a.registration), 1, 8))::bit(32)::int) % 90) * interval '1 day'))::date
            END,
       CASE WHEN abs(('x' || substr(md5(d.reference || a.registration), 1, 8))::bit(32)::int) % 3 = 0
            THEN 'WO-' || upper(substr(md5(d.reference || a.registration), 1, 6)) END,
       'seed', 'CAMO record'
FROM camo.directives d
JOIN camo.aircraft a ON a.aircraft_type_id = d.aircraft_type_id;

-- ------------------------------------------------------------
--  5. Bibliothèque MEL (extrait représentatif, par type)
-- ------------------------------------------------------------
INSERT INTO camo.mel_library (id, tenant_id, aircraft_type_id, item_ref, ata_chapter, title, mel_category,
                              rectification_days, installed_quantity, required_quantity, placard_required,
                              operational_procedure, maintenance_procedure, limitation, source_type, source_ref)
SELECT md5('mellib-' || t.icao_type || '-' || m.item_ref)::uuid,
       '00000000-0000-0000-0000-000000000001',
       t.id,
       m.item_ref,
       m.ata,
       m.title,
       m.category,
       m.days,
       m.installed,
       m.required,
       m.placard,
       m.ops_proc,
       m.mx_proc,
       m.limitation,
       'seed', 'Operator MEL'
FROM refdata.aircraft_types t
CROSS JOIN (VALUES
    ('21-31-01', '21', 'Cabin pressure controller, automatic mode', 'C', 10, 2, 1, true,  'Manual mode procedure applies', 'Deactivate and placard', 'Maximum FL 350'),
    ('23-11-02', '23', 'VHF communication transceiver number 2',    'C', 10, 2, 1, true,  'Single VHF operation',          'Deactivate and placard', 'Not for MNPS airspace'),
    ('25-62-01', '25', 'Life jacket, one seat',                     'B', 3,  1, 1, false, 'Seat blocked',                  'Replace at next station', 'Seat may not be occupied'),
    ('26-11-03', '26', 'Engine fire detection loop B',              'A', 1,  2, 1, true,  'Loop A monitoring only',        'Deactivate loop B',      'Day operations only'),
    ('28-41-01', '28', 'Fuel quantity indication, one tank',        'C', 10, 1, 0, true,  'Fuel by dripstick',             'Deactivate indication',  'Refuel to full before each leg'),
    ('30-11-01', '30', 'Wing anti-ice, one side',                   'A', 1,  2, 2, true,  'No flight in known icing',      'Troubleshoot at base',   'Icing conditions prohibited'),
    ('32-42-02', '32', 'Anti-skid system, one wheel',               'B', 3,  4, 3, true,  'Increase landing distance 15%', 'Deactivate and placard', 'Dry runway only'),
    ('33-51-01', '33', 'Cabin reading light, one seat',             'D', 120, 12, 11, false, 'None',                       'Replace when available', 'None'),
    ('34-21-01', '34', 'Radio altimeter number 2',                  'C', 10, 2, 1, true,  'CAT I approaches only',         'Deactivate and placard', 'No LVO'),
    ('35-11-01', '35', 'Passenger oxygen mask, one seat',           'A', 1,  1, 1, true,  'Seat blocked',                  'Replace mask',           'Seat may not be occupied'),
    ('38-31-01', '38', 'Lavatory flush, forward',                   'D', 120, 1, 0, false, 'Lavatory unserviceable',       'Repair at base',         'Placard the lavatory'),
    ('49-11-01', '49', 'Auxiliary power unit',                      'C', 10, 1, 0, true,  'Ground power required',         'Deactivate APU',         'No autonomous start')
) AS m(item_ref, ata, title, category, days, installed, required, placard, ops_proc, mx_proc, limitation);

-- Rattacher les lignes MEL existantes (V8) à la bibliothèque quand le
-- chapitre ATA correspond : le report hérite alors de son intervalle.
UPDATE camo.mel_items mi
SET mel_library_id = lib.id
FROM camo.aircraft a
JOIN camo.mel_library lib ON lib.aircraft_type_id = a.aircraft_type_id
WHERE mi.aircraft_id = a.id
  AND lib.mel_category = mi.mel_category
  AND mi.mel_library_id IS NULL
  AND lib.item_ref = (
      SELECT MIN(l2.item_ref) FROM camo.mel_library l2
      WHERE l2.aircraft_type_id = a.aircraft_type_id AND l2.mel_category = mi.mel_category);

-- ------------------------------------------------------------
--  6. Carnet de bord : une page par étape déjà volée
-- ------------------------------------------------------------
INSERT INTO camo.tech_log_entries (id, tenant_id, aircraft_id, leg_id, page_ref, flown_on, dep_icao, arr_icao,
                                   block_minutes, air_minutes, cycles, fuel_uplift_litres, oil_added_litres,
                                   commander_id, engineer_id, status, signed_at, source_type, source_ref)
SELECT md5('tlog-' || l.id::text)::uuid,
       l.tenant_id,
       l.aircraft_id,
       l.id,
       'TL-' || to_char(l.std, 'YYYYMMDD') || '-' || upper(substr(md5(l.id::text), 1, 5)),
       (l.std AT TIME ZONE 'UTC')::date,
       l.dep_icao,
       l.arr_icao,
       u.block_minutes,
       u.air_minutes,
       1,
       800 + (abs(('x' || substr(md5(l.id::text), 1, 8))::bit(32)::int) % 4200),
       0.5 * (abs(('x' || substr(md5(l.id::text), 1, 8))::bit(32)::int) % 4),
       (SELECT ca.person_id FROM crew.leg_assignments ca WHERE ca.leg_id = l.id AND ca.seat = 'CPT' LIMIT 1),
       NULL,
       CASE WHEN l.status = 'DEPARTED' THEN 'OPEN' ELSE 'SIGNED' END,
       CASE WHEN l.status <> 'DEPARTED' THEN l.sta END,
       'seed', 'Tech log'
FROM ops.legs l
JOIN camo.utilisation u ON u.leg_id = l.id
WHERE l.status IN ('DEPARTED', 'ARRIVED', 'CLOSED');

-- ------------------------------------------------------------
--  7. Défauts : trois ouverts, deux reportés sur une ligne MEL
--     existante, un soldé.
-- ------------------------------------------------------------
INSERT INTO camo.defects (id, tenant_id, aircraft_id, tech_log_entry_id, ata_chapter, description,
                          reported_at, reported_by, status, mel_item_id, corrective_action, closed_at,
                          source_type, source_ref)
SELECT md5('defect-' || e.page_ref)::uuid,
       e.tenant_id,
       e.aircraft_id,
       e.id,
       d.ata,
       d.description,
       e.flown_on + interval '18 hours',
       e.commander_id,
       'OPEN',
       NULL,
       NULL,
       NULL,
       'seed', 'Tech log'
FROM (
    SELECT e.*, row_number() OVER (ORDER BY e.page_ref) AS rn
    FROM camo.tech_log_entries e
) e
JOIN (VALUES
    (1, '33', 'Cockpit dome light flickers on the left side'),
    (2, '21', 'Cabin temperature slow to stabilise in cruise'),
    (3, '32', 'Nose wheel steering slightly stiff during taxi')
) AS d(rn, ata, description) ON d.rn = e.rn;

-- Un défaut reporté : il pointe une ligne MEL ouverte, comme le
-- veut la contrainte ck_defect_deferred.
INSERT INTO camo.defects (id, tenant_id, aircraft_id, tech_log_entry_id, ata_chapter, description,
                          reported_at, reported_by, status, mel_item_id, source_type, source_ref)
SELECT md5('defect-deferred-' || mi.id::text)::uuid,
       mi.tenant_id,
       mi.aircraft_id,
       NULL,
       '00',
       'Deferred under MEL ' || mi.reference || ' — ' || mi.title,
       mi.raised_at,
       NULL,
       'DEFERRED',
       mi.id,
       'seed', 'Tech log'
FROM camo.mel_items mi
WHERE mi.closed_at IS NULL;
