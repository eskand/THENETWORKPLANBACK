-- ============================================================
--  V16 — jeu de données Commercial et Sécurité, dérivé des
--  clients, des étapes et de l'équipage déjà semés.
-- ============================================================

-- ------------------------------------------------------------
--  1. Clients
-- ------------------------------------------------------------
INSERT INTO sales.clients (id, tenant_id, code, name, kind, country_iso2, email, phone, payment_terms, currency, source_type, source_ref)
SELECT md5('client-' || c.code)::uuid,
       '00000000-0000-0000-0000-000000000001',
       c.code, c.name, c.kind, c.country, c.email, c.phone, c.terms, c.currency,
       'seed', 'CRM'
FROM (VALUES
    ('MEDGRP',  'Mediterranean Energy Group', 'CORPORATE',  'TN', 'travel@medenergy.example',   '+216 71 100 100', '30 days', 'EUR'),
    ('ATLASBR', 'Atlas Brokerage',            'BROKER',     'FR', 'ops@atlasbroker.example',    '+33 1 45 00 00 00', 'Prepayment', 'EUR'),
    ('SAHARAM', 'Sahara Mining Ltd',          'CORPORATE',  'DZ', 'flights@saharamining.example', '+213 21 00 00 00', '45 days', 'USD'),
    ('MINFOR',  'Ministry of Foreign Affairs','GOVERNMENT', 'TN', 'protocol@mfa.example',       '+216 71 200 200', '60 days', 'EUR'),
    ('LIFELINE','Lifeline Air Ambulance',     'MEDICAL',    'MT', 'dispatch@lifeline.example',  '+356 21 00 00 00', 'Prepayment', 'EUR'),
    ('PRIVATE1','B. Haddad',                  'PRIVATE',    'TN', 'assistant@haddad.example',   '+216 98 000 000', 'Prepayment', 'EUR')
) AS c(code, name, kind, country, email, phone, terms, currency);

-- ------------------------------------------------------------
--  2. Demandes : quinze, réparties sur les statuts, dérivées des
--     paires de terrains réellement desservies par la flotte.
-- ------------------------------------------------------------
INSERT INTO sales.requests (id, tenant_id, reference, client_id, received_at, dep_icao, arr_icao,
                            departure_at, return_at, pax_count, flight_type, aircraft_type_id,
                            status, feasibility, feasibility_note, source_type, source_ref)
SELECT md5('req-' || r.n::text)::uuid,
       '00000000-0000-0000-0000-000000000001',
       'RFQ-' || to_char(date_trunc('day', now()) - (r.n * interval '2 days'), 'YYMMDD') || '-' || lpad(r.n::text, 2, '0'),
       cl.id,
       date_trunc('day', now()) - (r.n * interval '2 days') + interval '9 hours',
       route.dep_icao,
       route.arr_icao,
       date_trunc('day', now()) + ((r.n % 21) * interval '1 day') + interval '8 hours',
       CASE WHEN r.n % 3 = 0
            THEN date_trunc('day', now()) + ((r.n % 21) * interval '1 day') + interval '3 days 18 hours' END,
       2 + (r.n % 9),
       CASE WHEN r.n % 7 = 0 THEN 'AMBULANCE' ELSE 'PAX' END,
       t.id,
       CASE r.n % 5 WHEN 0 THEN 'WON' WHEN 1 THEN 'QUOTED' WHEN 2 THEN 'NEW'
                    WHEN 3 THEN 'QUOTED' ELSE 'LOST' END,
       CASE WHEN r.n % 4 = 0 THEN 'UNKNOWN'
            WHEN r.n % 4 = 1 THEN 'FEASIBLE'
            WHEN r.n % 4 = 2 THEN 'CONDITIONAL'
            ELSE 'FEASIBLE' END,
       CASE WHEN r.n % 4 = 2 THEN 'Subject to a landing permit and a slot at destination'
            WHEN r.n % 4 = 0 THEN 'Not assessed yet' END,
       'seed', 'CRM'
FROM generate_series(1, 15) AS r(n)
CROSS JOIN LATERAL (
    SELECT id FROM sales.clients ORDER BY md5('c' || r.n::text || code) LIMIT 1
) cl
CROSS JOIN LATERAL (
    SELECT dep_icao, arr_icao FROM ops.legs ORDER BY md5('l' || r.n::text || id::text) LIMIT 1
) route
CROSS JOIN LATERAL (
    SELECT id FROM refdata.aircraft_types ORDER BY md5('t' || r.n::text || icao_type) LIMIT 1
) t;

-- ------------------------------------------------------------
--  3. Devis et lignes de devis.
--     Chaque ligne porte sa devise et son taux : le total est une
--     somme de montants convertis.
-- ------------------------------------------------------------
INSERT INTO sales.quotes (id, tenant_id, request_id, reference, version, currency, status,
                          valid_until, sent_at, decided_at, source_type, source_ref)
SELECT md5('quote-' || r.reference)::uuid,
       r.tenant_id,
       r.id,
       replace(r.reference, 'RFQ', 'QUO'),
       1,
       'EUR',
       CASE r.status WHEN 'WON' THEN 'ACCEPTED' WHEN 'LOST' THEN 'REFUSED'
                     WHEN 'QUOTED' THEN 'SENT' ELSE 'DRAFT' END,
       (r.received_at + interval '14 days')::date,
       CASE WHEN r.status <> 'NEW' THEN r.received_at + interval '4 hours' END,
       CASE WHEN r.status IN ('WON', 'LOST') THEN r.received_at + interval '2 days' END,
       'seed', 'CRM'
FROM sales.requests r
WHERE r.status <> 'CANCELLED';

INSERT INTO sales.quote_lines (id, tenant_id, quote_id, line_no, kind, label, quantity, unit,
                               unit_price, currency, fx_rate, fx_rate_at, taxable, source_type, source_ref)
SELECT md5('qline-' || q.reference || '-' || l.line_no::text)::uuid,
       q.tenant_id,
       q.id,
       l.line_no,
       l.kind,
       l.label,
       l.quantity,
       l.unit,
       l.unit_price,
       l.currency,
       -- Taux figé au moment du devis : USD -> EUR à 0,92, EUR -> EUR à 1.
       CASE WHEN l.currency = 'USD' THEN 0.92 ELSE 1 END,
       (q.created_at)::date,
       l.taxable,
       'seed', 'Price list'
FROM sales.quotes q
CROSS JOIN (VALUES
    (1, 'FLIGHT_HOUR', 'Block hours, quoted aircraft', 4.5, 'h',  4200.00, 'EUR', true),
    (2, 'POSITIONING', 'Positioning to departure',     1.5, 'h',  3200.00, 'EUR', true),
    (3, 'HANDLING',    'Handling, both stations',      2.0, 'ea',  850.00, 'EUR', true),
    (4, 'FUEL',        'Fuel uplift at destination',   3200, 'l',     1.05, 'USD', true),
    (5, 'OVERFLIGHT',  'Overflight and landing permits', 1.0, 'ea', 640.00, 'EUR', true),
    (6, 'CATERING',    'Catering, standard menu',      1.0, 'ea',  420.00, 'EUR', true)
) AS l(line_no, kind, label, quantity, unit, unit_price, currency, taxable);

-- ------------------------------------------------------------
--  4. Matrice de risque 5x5 de l'exploitant (ICAO doc 9859).
-- ------------------------------------------------------------
INSERT INTO safety.risk_matrix (id, tenant_id, severity, probability, risk_level, action_required, source_type, source_ref)
SELECT md5('matrix-' || s.severity || p.probability::text)::uuid,
       '00000000-0000-0000-0000-000000000001',
       s.severity,
       p.probability,
       CASE
         WHEN s.rank * p.probability >= 15 THEN 'UNACCEPTABLE'
         WHEN s.rank * p.probability >= 6  THEN 'TOLERABLE'
         ELSE 'ACCEPTABLE'
       END,
       CASE
         WHEN s.rank * p.probability >= 15 THEN 'Stop the activity; mitigation required before resuming'
         WHEN s.rank * p.probability >= 6  THEN 'Mitigation required, accountable manager informed'
         ELSE 'Acceptable as it stands; monitor'
       END,
       'seed', 'ICAO Doc 9859 matrix'
FROM (VALUES ('A', 5), ('B', 4), ('C', 3), ('D', 2), ('E', 1)) AS s(severity, rank)
CROSS JOIN generate_series(1, 5) AS p(probability);

-- ------------------------------------------------------------
--  5. Occurrences : douze, dont trois évaluées, deux fermées.
-- ------------------------------------------------------------
INSERT INTO safety.occurrences (id, tenant_id, reference, occurred_at, reported_at, reported_by, anonymous,
                                leg_id, aircraft_id, station_icao, category, title, narrative, phase_of_flight,
                                eccairs_event_type, eccairs_occurrence_class,
                                risk_severity, risk_probability, risk_level, risk_assessed_at, risk_assessed_by,
                                status, source_type, source_ref)
SELECT md5('occ-' || o.n::text)::uuid,
       '00000000-0000-0000-0000-000000000001',
       'OCC-' || to_char(date_trunc('day', now()) - (o.n * interval '6 days'), 'YYYY') || '-' || lpad(o.n::text, 3, '0'),
       date_trunc('day', now()) - (o.n * interval '6 days') + interval '11 hours',
       date_trunc('day', now()) - (o.n * interval '6 days') + interval '15 hours',
       CASE WHEN o.n % 4 <> 0 THEN p.id END,
       o.n % 4 = 0,
       CASE WHEN o.n % 3 = 0 THEN l.id END,
       a.id,
       o.station,
       o.category,
       o.title,
       o.narrative,
       o.phase,
       o.eccairs_type,
       CASE WHEN o.n <= 2 THEN 'SERIOUS_INCIDENT' ELSE 'INCIDENT' END,
       CASE WHEN o.n <= 5 THEN o.severity END,
       CASE WHEN o.n <= 5 THEN o.probability END,
       CASE WHEN o.n <= 5 THEN m.risk_level END,
       CASE WHEN o.n <= 5 THEN date_trunc('day', now()) - (o.n * interval '5 days') END,
       CASE WHEN o.n <= 5 THEN 'd007ca51-8be0-5abf-b41a-0f13a4ee1625'::uuid END,
       CASE WHEN o.n <= 2 THEN 'CLOSED' WHEN o.n <= 5 THEN 'ACTIONS_OPEN'
            WHEN o.n <= 8 THEN 'UNDER_REVIEW' ELSE 'REPORTED' END,
       'seed', 'Safety report'
FROM (VALUES
    (1,  'DTTA', 'TECHNICAL',   'Hydraulic system 2 low pressure warning in cruise', 'The crew received a HYD 2 LO PRESS caution at FL370. Checklist actioned, no further indication. Uneventful landing.', 'CRUISE', 'System failure', 'B', 3),
    (2,  'LFPG', 'OPERATIONAL', 'Runway incursion by a service vehicle',             'A catering vehicle crossed the holding point while the aircraft was lined up. Take-off was rejected below 40 kt.', 'TAKEOFF', 'Runway incursion', 'B', 2),
    (3,  'DNMM', 'GROUND',      'Ground handling damage to the cargo door seal',     'Loader contacted the cargo door frame. Damage found at post-flight inspection.', 'GROUND', 'Ground damage', 'C', 3),
    (4,  'LGAV', 'WEATHER',     'Severe turbulence in descent',                      'Moderate to severe turbulence encountered in descent. One cabin crew member bruised.', 'DESCENT', 'Turbulence encounter', 'C', 4),
    (5,  'LSGG', 'BIRD_STRIKE', 'Bird strike on approach, no damage',                'Single bird strike on the radome at 400 ft. Inspection found no damage.', 'APPROACH', 'Bird strike', 'D', 4),
    (6,  'DTTA', 'CABIN',       'Cabin door warning after boarding',                 'Door warning remained after closure. Door recycled, warning cleared.', 'GROUND', 'System failure', NULL, NULL),
    (7,  'HECA', 'ATC',         'Level bust of 400 ft during climb',                 'Aircraft climbed 400 ft above cleared level. ATC advised, corrected immediately.', 'CLIMB', 'Level bust', NULL, NULL),
    (8,  'LFML', 'TECHNICAL',   'APU auto shutdown on the ground',                   'APU shut down automatically during turnaround. Ground power used.', 'GROUND', 'System failure', NULL, NULL),
    (9,  'LMML', 'FUEL',        'Fuel uplift discrepancy of 300 kg',                 'Uplift figure differed from the fuel receipt. Reconciled with the supplier.', 'GROUND', 'Fuel related', NULL, NULL),
    (10, 'OMDB', 'SECURITY',    'Unattended baggage at the FBO',                     'Unattended bag reported to airport security. Owner identified.', 'GROUND', 'Security', NULL, NULL),
    (11, 'DTTA', 'MEDICAL',     'Passenger unwell during boarding',                  'Passenger reported chest pain during boarding. Medical services attended, flight delayed 45 min.', 'GROUND', 'Medical', NULL, NULL),
    (12, 'LEMD', 'OPERATIONAL', 'Late change of runway in use',                      'Runway change on short final led to a go-around. Uneventful second approach.', 'APPROACH', 'Go around', NULL, NULL)
) AS o(n, station, category, title, narrative, phase, eccairs_type, severity, probability)
CROSS JOIN LATERAL (SELECT id FROM crew.persons ORDER BY md5('p' || o.n::text || staff_no) LIMIT 1) p
CROSS JOIN LATERAL (SELECT id FROM camo.aircraft ORDER BY md5('a' || o.n::text || registration) LIMIT 1) a
CROSS JOIN LATERAL (SELECT id FROM ops.legs ORDER BY md5('g' || o.n::text || id::text) LIMIT 1) l
LEFT JOIN safety.risk_matrix m
       ON m.severity = o.severity AND m.probability = o.probability;

-- ------------------------------------------------------------
--  6. Actions correctives sur les occurrences évaluées
-- ------------------------------------------------------------
INSERT INTO safety.actions (id, tenant_id, occurrence_id, reference, title, detail, owner_user_id,
                            due_on, status, completed_on, effectiveness, source_type, source_ref)
SELECT md5('act-' || o.reference || '-' || a.n::text)::uuid,
       o.tenant_id,
       o.id,
       'ACT-' || substr(o.reference, 5) || '-' || a.n::text,
       a.title,
       a.detail,
       'd007ca51-8be0-5abf-b41a-0f13a4ee1625',
       (date_trunc('day', now()) + ((a.n * 20) - 10) * interval '1 day')::date,
       CASE WHEN o.status = 'CLOSED' THEN 'COMPLETED' WHEN a.n = 1 THEN 'IN_PROGRESS' ELSE 'OPEN' END,
       CASE WHEN o.status = 'CLOSED' THEN (date_trunc('day', now()) - interval '10 days')::date END,
       CASE WHEN o.status = 'CLOSED' THEN 'EFFECTIVE' ELSE 'NOT_ASSESSED' END,
       'seed', 'Safety action plan'
FROM safety.occurrences o
CROSS JOIN (VALUES
    (1, 'Review the procedure with the crews concerned', 'Briefing note issued and acknowledged.'),
    (2, 'Amend the checklist and reissue',               'Amendment submitted to the authority.')
) AS a(n, title, detail)
WHERE o.risk_level IS NOT NULL;

-- ------------------------------------------------------------
--  7. Promotion de la sécurité
-- ------------------------------------------------------------
INSERT INTO safety.campaigns (id, tenant_id, reference, title, theme, message, starts_on, ends_on,
                              audience, status, acknowledgement_required, source_type, source_ref)
SELECT md5('camp-' || c.reference)::uuid,
       '00000000-0000-0000-0000-000000000001',
       c.reference, c.title, c.theme, c.message,
       (date_trunc('day', now()) + (c.starts * interval '1 day'))::date,
       (date_trunc('day', now()) + (c.ends * interval '1 day'))::date,
       c.audience, c.status, c.ack,
       'seed', 'Safety promotion plan'
FROM (VALUES
    ('CMP-2026-01', 'Runway safety, hold short discipline', 'RUNWAY_SAFETY',  'Read back, then hold. Every incursion this year started with an assumed clearance.', -40, -5, 'FLIGHT_CREW', 'CLOSED', true),
    ('CMP-2026-02', 'Reporting culture: report it, we fix it', 'JUST_CULTURE', 'A report is not a blame. Anonymous reporting is available on every station.', -20, 25, 'ALL', 'RUNNING', true),
    ('CMP-2026-03', 'Ground damage prevention',            'GROUND_SAFETY',  'Walk the wing tip. Two of our last ten occurrences were ground contacts.', -10, 35, 'GROUND', 'RUNNING', false),
    ('CMP-2026-04', 'Fatigue: say it before the duty',     'FATIGUE',        'Declaring fatigue is a duty, not a fault. The roster team will re-crew.', 15, 60, 'FLIGHT_CREW', 'PLANNED', true)
) AS c(reference, title, theme, message, starts, ends, audience, status, ack);

INSERT INTO safety.campaign_acknowledgements (id, tenant_id, campaign_id, person_id, acknowledged_at, source_type, source_ref)
SELECT md5('ack-' || c.reference || p.staff_no)::uuid,
       c.tenant_id, c.id, p.id,
       c.starts_on + interval '2 days',
       'seed', 'Safety promotion'
FROM safety.campaigns c
JOIN crew.persons p
  ON abs(('x' || substr(md5(c.reference || p.staff_no), 1, 8))::bit(32)::int) % 3 <> 0
WHERE c.acknowledgement_required = true
  AND c.status <> 'PLANNED';

-- ------------------------------------------------------------
--  8. Plan d'urgence
-- ------------------------------------------------------------
INSERT INTO safety.erp_plans (id, tenant_id, code, title, revision, approved_on, review_due_on, summary, source_type, source_ref) VALUES
  (md5('erp-main')::uuid, '00000000-0000-0000-0000-000000000001', 'ERP-01',
   'Emergency Response Plan', 'Rev 6',
   (date_trunc('day', now()) - interval '200 days')::date,
   (date_trunc('day', now()) + interval '165 days')::date,
   'Activation criteria, crisis centre, call tree, family assistance and media handling.',
   'seed', 'Operations manual part A');

INSERT INTO safety.erp_roles (id, tenant_id, plan_id, role_code, role_title, holder_user_id, deputy_user_id,
                              phone, responsibilities, call_order, source_type, source_ref)
SELECT md5('erprole-' || r.role_code)::uuid,
       '00000000-0000-0000-0000-000000000001',
       md5('erp-main')::uuid,
       r.role_code, r.role_title,
       'd007ca51-8be0-5abf-b41a-0f13a4ee1625',
       '9742ecbf-60e4-5850-8d6b-ec3de60e3540',
       r.phone, r.responsibilities, r.call_order,
       'seed', 'Operations manual part A'
FROM (VALUES
    ('ERD',  'Emergency Response Director', '+216 98 100 001', 'Declares the activation, chairs the crisis centre.', 1),
    ('OPS',  'Operations coordinator',      '+216 98 100 002', 'Aircraft, crew and passenger status.', 2),
    ('TECH', 'Technical coordinator',       '+216 98 100 003', 'Airworthiness, investigation liaison.', 3),
    ('CARE', 'Family assistance leader',    '+216 98 100 004', 'Humanitarian assistance, next of kin.', 4),
    ('COMM', 'Communications leader',       '+216 98 100 005', 'Media statements, single point of contact.', 5),
    ('LEGAL','Legal and insurance',         '+216 98 100 006', 'Insurers, authorities, records preservation.', 6)
) AS r(role_code, role_title, phone, responsibilities, call_order);

INSERT INTO safety.erp_activations (id, tenant_id, plan_id, kind, reference, leg_id, activated_at,
                                    activated_by, stood_down_at, situation, source_type, source_ref)
VALUES
  (md5('erpact-1')::uuid, '00000000-0000-0000-0000-000000000001', md5('erp-main')::uuid,
   'EXERCISE', 'ERP-EX-2026-01', NULL,
   date_trunc('day', now()) - interval '75 days' + interval '9 hours',
   'd007ca51-8be0-5abf-b41a-0f13a4ee1625',
   date_trunc('day', now()) - interval '75 days' + interval '14 hours',
   'Annual table-top exercise: runway excursion at DTTA, 9 souls on board.', 'seed', 'ERP exercise report'),
  (md5('erpact-2')::uuid, '00000000-0000-0000-0000-000000000001', md5('erp-main')::uuid,
   'STANDBY', 'ERP-SB-2026-02', NULL,
   date_trunc('day', now()) - interval '30 days' + interval '18 hours',
   '9742ecbf-60e4-5850-8d6b-ec3de60e3540',
   date_trunc('day', now()) - interval '30 days' + interval '21 hours',
   'Precautionary standby: medical diversion to LMML, stood down after landing.', 'seed', 'ERP log');
