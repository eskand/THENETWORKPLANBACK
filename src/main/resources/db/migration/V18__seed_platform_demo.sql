-- ============================================================
--  V18 — paramètres, rapports et scénarios.
--
--  Chaque paramètre nomme le service qui le lit. Les valeurs
--  reprises ici sont celles que le code utilise réellement
--  (OpsProperties, CrewProperties, les fenêtres d'alerte), pas
--  des réglages décoratifs.
-- ============================================================

INSERT INTO platform.settings (id, tenant_id, category, setting_key, setting_value, value_type, unit,
                               label, description, read_by, editable, source_type, source_ref)
SELECT md5('set-' || s.setting_key)::uuid,
       '00000000-0000-0000-0000-000000000001',
       s.category, s.setting_key, s.setting_value, s.value_type, s.unit,
       s.label, s.description, s.read_by, s.editable,
       'seed', 'Operations manual'
FROM (VALUES
    ('OPS', 'ops.minimum-turnaround', '50', 'INTEGER', 'minutes',
     'Minimum turnaround',
     'Below this, a delay on one leg pushes the next rotation of the same tail, and the timeline marks the turnaround as tight.',
     'OpsProperties, TimelineService, LegServiceImpl', true),
    ('OPS', 'ops.delay-threshold', '15', 'INTEGER', 'minutes',
     'Delay threshold',
     'A leg counts as delayed once the revised departure exceeds the schedule by this.',
     'OpsProperties, DispatchBoardService', true),
    ('OPS', 'ops.position-stale-after', '15', 'INTEGER', 'minutes',
     'Position considered stale',
     'Beyond this, the last position of a flight is shown as STALE on Flight Following.',
     'FlightFollowingService', true),
    ('CREW', 'crew.minimum-rest', '720', 'INTEGER', 'minutes',
     'Minimum rest (operator threshold)',
     'Operator threshold used by crew scheduling. NOT an ORO.FTL.235 verdict: the regulatory computation belongs to the FTL engine of sprint S7.',
     'CrewProperties, CrewAvailabilityChecker', true),
    ('CREW', 'crew.report-before-std', '75', 'INTEGER', 'minutes',
     'Reporting time before STD',
     'Used to build the duty period when a seat is assigned.',
     'CrewProperties, CrewSchedulingService', true),
    ('CREW', 'crew.document-warning-window', '30', 'INTEGER', 'days',
     'Document warning window',
     'A licence, medical or training certificate inside this window is shown as EXPIRING.',
     'CrewDocumentChecker', true),
    ('MAINTENANCE', 'camo.due-warning-days', '30', 'INTEGER', 'days',
     'Maintenance due warning, calendar',
     'A task within this many days of its calendar limit is DUE_SOON.',
     'MaintenanceDueRule', true),
    ('MAINTENANCE', 'camo.due-warning-hours', '50', 'INTEGER', 'flight hours',
     'Maintenance due warning, hours',
     'A task within this many flight hours of its limit is DUE_SOON.',
     'MaintenanceDueRule', true),
    ('TRIP_SUPPORT', 'tripsupport.expected-services', 'HANDLING,FUEL', 'ENUM', NULL,
     'Services expected at every station',
     'A leg with no request for one of these is flagged as "not requested" on the NetPlus Services board.',
     'TripSupportBoardService', true),
    ('SAFETY', 'safety.matrix-revision', 'ICAO Doc 9859, 5x5', 'STRING', NULL,
     'Risk matrix in force',
     'The matrix itself lives in safety.risk_matrix; this key records which revision the operator has adopted.',
     'SafetyService', false),
    ('COMMERCIAL', 'sales.pipeline-currency', 'EUR', 'STRING', NULL,
     'Pipeline currency',
     'Quotes in another currency are excluded from the pipeline tiles rather than added raw.',
     'SalesService', true),
    ('PLATFORM', 'platform.dispatch-board-cache', '20', 'INTEGER', 'seconds',
     'Dispatch board cache',
     'How long the rendered dispatch board is cached, so a room full of dispatchers costs one query set.',
     'TwoLevelCacheManager', true)
) AS s(category, setting_key, setting_value, value_type, unit, label, description, read_by, editable);

-- ------------------------------------------------------------
--  Rapports : la définition. Le résultat est recalculé.
-- ------------------------------------------------------------
INSERT INTO platform.report_definitions (id, tenant_id, code, title, domain, description,
                                         default_window_days, source_type, source_ref)
SELECT md5('rep-' || r.code)::uuid,
       '00000000-0000-0000-0000-000000000001',
       r.code, r.title, r.domain, r.description, r.window_days,
       'seed', 'Reporting catalogue'
FROM (VALUES
    ('OPS-UTIL',   'Fleet utilisation',            'OPS',
     'Block hours, cycles and flights per registration over the window, from camo.utilisation.', 30),
    ('OPS-OTP',    'On-time performance',          'OPS',
     'Departures within fifteen minutes of schedule, over the legs that actually departed.', 30),
    ('OPS-DELAY',  'Delays by IATA code',          'OPS',
     'Minutes and occurrences per delay code, from ops.delay_records.', 90),
    ('CREW-FTL',   'Crew block time',              'CREW',
     'Block minutes per crew member over 7, 28 and 365 days, from crew.duty_periods.', 28),
    ('CREW-EXP',   'Crew expiries',                'CREW',
     'Licences, medicals, training and qualifications lapsing inside the window.', 90),
    ('MX-DUE',     'Maintenance due list',         'MAINTENANCE',
     'Tasks overdue or due inside the window, with the limit that drives each.', 90),
    ('MX-MEL',     'MEL items in force',           'MAINTENANCE',
     'Deferred defects, their category and their rectification deadline.', 30),
    ('TS-PERMITS', 'Permits outstanding',          'TRIP_SUPPORT',
     'Permit requests not confirmed, by country and by leg.', 30),
    ('SAF-OCC',    'Occurrences by category',      'SAFETY',
     'Reports per category and per risk level, filed and unfiled.', 365),
    ('COM-PIPE',   'Sales pipeline',               'COMMERCIAL',
     'Requests, quotes and their converted totals, by status and by client.', 90)
) AS r(code, title, domain, description, window_days);

-- Quelques exécutions passées, pour que la page ait une histoire.
INSERT INTO platform.report_runs (id, tenant_id, definition_id, ran_at, ran_by, window_from, window_to,
                                  row_count, duration_ms, source_type, source_ref)
SELECT md5('run-' || d.code || '-' || n.i::text)::uuid,
       d.tenant_id,
       d.id,
       date_trunc('day', now()) - (n.i * interval '7 days') + interval '7 hours',
       'd007ca51-8be0-5abf-b41a-0f13a4ee1625',
       (date_trunc('day', now()) - (n.i * interval '7 days') - (d.default_window_days * interval '1 day'))::date,
       (date_trunc('day', now()) - (n.i * interval '7 days'))::date,
       10 + (abs(('x' || substr(md5(d.code || n.i::text), 1, 8))::bit(32)::int) % 90),
       40 + (abs(('x' || substr(md5(d.code || n.i::text), 1, 8))::bit(32)::int) % 400),
       'seed', 'Reporting'
FROM platform.report_definitions d
CROSS JOIN generate_series(1, 3) AS n(i);

-- ------------------------------------------------------------
--  Scénarios de simulation. Les références sont textuelles :
--  aucune clé étrangère vers ops, donc aucun risque d'écrire
--  dans le programme réel.
-- ------------------------------------------------------------
INSERT INTO planning.simulation_scenarios (id, tenant_id, code, title, kind, narrative, baseline_date,
                                           status, last_run_at, last_run_by, source_type, source_ref)
SELECT md5('scn-' || s.code)::uuid,
       '00000000-0000-0000-0000-000000000001',
       s.code, s.title, s.kind, s.narrative,
       date_trunc('day', now())::date,
       s.status,
       CASE WHEN s.status = 'RUN' THEN date_trunc('day', now()) - interval '12 days' END,
       CASE WHEN s.status = 'RUN' THEN 'd007ca51-8be0-5abf-b41a-0f13a4ee1625'::uuid END,
       'seed', 'Training scenarios'
FROM (VALUES
    ('SIM-AOG-01', 'AOG at an outstation, four legs affected', 'AOG',
     'TS-NPK goes AOG at LFPB after the first sector. Four legs and nine passengers are affected. The exercise measures how quickly a replacement tail and crew are found.',
     'RUN'),
    ('SIM-WX-01',  'Fog at the home base, LVP in force', 'WEATHER',
     'DTTA goes below CAT I minima for four hours. Arrivals must hold or divert; departures need a take-off alternate.',
     'READY'),
    ('SIM-CRW-01', 'Captain unfit before a long sector', 'CREW_SHORTAGE',
     'The commander declares unfit ninety minutes before departure of a five-hour sector. Only two rated captains are within reach and one is inside minimum rest.',
     'READY'),
    ('SIM-ASP-01', 'Airspace closure over a transit country', 'AIRSPACE',
     'A FIR on the route closes at short notice. Route, fuel and overflight permits all have to be reconsidered.',
     'DRAFT')
) AS s(code, title, kind, narrative, status);

INSERT INTO planning.simulation_events (id, tenant_id, scenario_id, sequence_no, offset_minutes, kind,
                                        registration, flight_no, station_icao, detail, expected_action,
                                        source_type, source_ref)
SELECT md5('scnev-' || e.code || '-' || e.sequence_no::text)::uuid,
       '00000000-0000-0000-0000-000000000001',
       md5('scn-' || e.code)::uuid,
       e.sequence_no, e.offset_minutes, e.kind,
       e.registration, e.flight_no, e.station_icao, e.detail, e.expected_action,
       'seed', 'Training scenarios'
FROM (VALUES
    ('SIM-AOG-01', 1, 0,   'AOG_DECLARED',     'TS-NPK', NULL,     'LFPB', 'Left main gear actuator leak found at post-flight. Aircraft unserviceable.', 'Declare AOG, notify CAMO, hold the passengers'),
    ('SIM-AOG-01', 2, 15,  'DECISION_POINT',   'TS-NPK', 'TNP118', 'LFPB', 'Which tail replaces it, and is its crew legal for the remaining sectors?', 'Use Crew Scheduling to test the pool'),
    ('SIM-AOG-01', 3, 45,  'PERMIT_REFUSED',   NULL,     'TNP118', NULL,   'The replacement tail has no overflight permit for the transit country.', 'Raise a permit request or re-route'),
    ('SIM-AOG-01', 4, 120, 'INJECT',           NULL,     NULL,     'LFPB', 'The client asks for a written plan within thirty minutes.', 'Produce the revised programme'),
    ('SIM-WX-01',  1, 0,   'AIRPORT_CLOSED',   NULL,     NULL,     'DTTA', 'RVR falls to 250 m. LVP in force, CAT I aerodrome.', 'Check crew LVO qualification and aircraft capability'),
    ('SIM-WX-01',  2, 30,  'DIVERSION',        NULL,     'TNP101', 'DTTA', 'Inbound flight cannot land and diverts to DTNH.', 'Handle the diversion, passengers and crew duty'),
    ('SIM-WX-01',  3, 90,  'DECISION_POINT',   NULL,     NULL,     'DTNH', 'Do the crews remain legal to reposition the aircraft tonight?', 'Check duty time before repositioning'),
    ('SIM-CRW-01', 1, 0,   'CREW_UNAVAILABLE', NULL,     'TNP120', 'DTTA', 'The commander declares unfit ninety minutes before departure.', 'Find a rated captain within rest'),
    ('SIM-CRW-01', 2, 20,  'DECISION_POINT',   NULL,     'TNP120', 'DTTA', 'The only available captain is fifty minutes short of the operator minimum rest.', 'Delay, or find another; do not derogate silently'),
    ('SIM-ASP-01', 1, 0,   'AIRSPACE_CLOSED',  NULL,     NULL,     NULL,   'A FIR on the route closes at four hours notice.', 'Re-route, recompute fuel, re-file permits')
) AS e(code, sequence_no, offset_minutes, kind, registration, flight_no, station_icao, detail, expected_action)
WHERE e.kind <> 'AIRSPACE_CLOSED';

-- L'événement de fermeture d'espace utilise le libellé admis par la contrainte.
INSERT INTO planning.simulation_events (id, tenant_id, scenario_id, sequence_no, offset_minutes, kind,
                                        detail, expected_action, source_type, source_ref)
VALUES (md5('scnev-SIM-ASP-01-1')::uuid, '00000000-0000-0000-0000-000000000001',
        md5('scn-SIM-ASP-01')::uuid, 1, 0, 'AIRPORT_CLOSED',
        'A FIR on the route closes at four hours notice.',
        'Re-route, recompute fuel, re-file permits', 'seed', 'Training scenarios');
