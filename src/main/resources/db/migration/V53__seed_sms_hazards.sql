-- ============================================================
--  V53 — le contenu du registre.
--
--  Cinq dangers, seize barrieres, six retours d'experience avec leurs
--  dix-huit lecons, cinq notifications. Source : l'annexe A4.
--
--  LES LECTURES DE REX NE SONT PAS SEMEES. Le prototype garde un
--  compteur « reads: 96 » sans savoir qui a lu. On ne peut pas
--  inventer quatre-vingt-seize lecteurs : le compteur repart de zero,
--  ce qui est faux de moins que quatre-vingt-seize noms inventes.
-- ============================================================

-- Les dangers identifies
INSERT INTO safety.hazards
    (id, tenant_id, source_type, source_ref, reference, hazard, consequence, domain,
     category, identification, severity_initial, likelihood_initial,
     severity_residual, likelihood_residual, owner, review_on, status, notes)
VALUES
  (md5('hz:'||'RSK-2026-0001')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.risks', 'RSK-2026-0001', 'Cumulative crew fatigue on consecutive night operations', 'Degraded crew performance leading to an operational error during a critical phase of flight', 'CREW', 'Human factors', 'reactive', 'B', 4, 'B', 2, 'Dupont A.', '2026-10-01'::date, 'mitigating', 'Monitored under the FRMS. ORO.FTL.205 compliance alone is not treated as sufficient evidence of adequate rest.'),
  (md5('hz:'||'RSK-2026-0002')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.risks', 'RSK-2026-0002', 'NOTAM and airspace restriction data not fully integrated into the OFP', 'Airspace infringement or unplanned deviation in controlled or restricted airspace', 'OCC', 'Flight planning', 'reactive', 'B', 4, 'C', 2, 'Martin J.', '2026-09-15'::date, 'mitigating', NULL),
  (md5('hz:'||'RSK-2026-0003')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.risks', 'RSK-2026-0003', 'Wildlife presence at aerodromes on the network', 'Bird or animal strike causing engine damage or loss of thrust on take-off or approach', 'OPS', 'Wildlife', 'reactive', 'A', 3, 'B', 2, 'Dupont A.', '2026-09-30'::date, 'mitigating', NULL),
  (md5('hz:'||'RSK-2026-0004')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.risks', 'RSK-2026-0004', 'Deferred defects accumulating on a single airframe under the MEL', 'Cumulative degradation of aircraft systems reducing redundancy below the intended design margin', 'TECHLOG', 'Technical', 'proactive', 'B', 3, 'C', 2, 'Haddad J.', '2026-08-31'::date, 'monitored', 'Monitored automatically by the SMS cross-module scan.'),
  (md5('hz:'||'RSK-2026-0005')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.risks', 'RSK-2026-0005', 'Operation into Category C aerodromes without a current commander qualification', 'Approach or landing incident at a demanding aerodrome', 'AIRPORTS', 'Aerodrome', 'proactive', 'B', 3, 'C', 1, 'Martin J.', '2026-11-01'::date, 'monitored', NULL);

-- Les barrieres
INSERT INTO safety.hazard_controls
    (tenant_id, hazard_id, description, control_type, owner, status, sort_order)
VALUES
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0001')::uuid, 'Maximum two consecutive night sectors in the roster build rules', 'preventive', 'Belhadj S.', 'in-place', 1),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0001')::uuid, 'Confidential fatigue reporting with no-blame guarantee (Just Culture policy)', 'preventive', 'Safety Manager', 'in-place', 2),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0001')::uuid, 'Standby crew coverage on every night rotation', 'recovery', 'OCC', 'in-place', 3),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0001')::uuid, 'FRMS fatigue scoring integrated into roster publication', 'preventive', 'Belhadj S.', 'planned', 4),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0002')::uuid, 'Mandatory dispatcher NOTAM cross-check on the flight planning checklist', 'preventive', 'OCC Manager', 'in-place', 1),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0002')::uuid, 'Second-person verification for flights entering restricted or conflict-zone airspace', 'preventive', 'OCC Manager', 'in-place', 2),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0002')::uuid, 'Crew NOTAM briefing pack attached to every OFP', 'recovery', 'Dispatch', 'in-place', 3),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0003')::uuid, 'Wildlife activity check included in the pre-departure aerodrome briefing', 'preventive', 'Dispatch', 'in-place', 1),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0003')::uuid, 'Strike reporting to the aerodrome operator and the authority', 'recovery', 'Safety Manager', 'in-place', 2),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0003')::uuid, 'Seasonal wildlife risk assessment for high-activity aerodromes', 'preventive', 'Safety Manager', 'planned', 3),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0004')::uuid, 'Daily review of open and deferred defects by the CAMO and the Safety Manager', 'preventive', 'CAMO', 'in-place', 1),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0004')::uuid, 'Escalation when three or more MEL items are open on one tail', 'preventive', 'CAMO', 'in-place', 2),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0004')::uuid, 'Automatic SMS alert when a rectification interval is exceeded', 'recovery', 'Safety Manager', 'in-place', 3),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0005')::uuid, 'Aerodrome categorisation held in the Airport Data register as the single source of truth', 'preventive', 'Flight Ops', 'in-place', 1),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0005')::uuid, 'Roster rule preventing an unqualified commander from being assigned a CAT C sector', 'preventive', 'Belhadj S.', 'in-place', 2),
  ('00000000-0000-0000-0000-000000000001'::uuid, md5('hz:'||'RSK-2026-0005')::uuid, 'Route and aerodrome briefing issued with the OFP', 'preventive', 'Dispatch', 'in-place', 3);

-- Les occurrences de l annexe A4
--
-- Elles s AJOUTENT au registre : les douze deja presentes portent
-- dix actions correctives et des enquetes. Les supprimer pour
-- faire tomber un compteur juste aurait detruit ce qui pend
-- dessous, et un registre se complete, il ne se remplace pas.
INSERT INTO safety.occurrences
    (id, tenant_id, source_type, source_ref, reference, occurred_at, reported_at,
     reported_by, reporter_name, reporter_role, anonymous, confidential, station_icao,
     category, title, narrative, phase_of_flight, eccairs_occurrence_class,
     eccairs_reference, risk_severity, risk_probability, risk_level,
     status, report_type, immediate_action)
SELECT md5('occ:'||'OCC-2026-0113')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.occurrences', 'OCC-2026-0113', '2026-07-27T06:42:00Z'::timestamptz, '2026-07-27T06:42:00Z'::timestamptz, (SELECT p.id FROM crew.persons p WHERE p.last_name = 'Ben Arbia' AND p.first_name = 'Y.' AND p.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid LIMIT 1), 'Ben Arbia Y.', 'Commander', false, false, 'LFPG', 'BIRD_STRIKE', 'Bird strike on final approach — LFPG RWY 27R', 'Bird strike on the left engine nacelle during final approach RWY 27R. Engine parameters remained nominal, no vibration. Aircraft landed normally. Post-flight inspection found minor nacelle lip damage. No injuries.', 'Approach', 'SERIOUS_INCIDENT', 'MOR/2026/0441', 'B', 3, 'TOLERABLE', 'UNDER_REVIEW', 'INCIDENT', 'Aircraft withdrawn from service for boroscope inspection. Wildlife control notified via ATC.'
UNION ALL
SELECT md5('occ:'||'OCC-2026-0112')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.occurrences', 'OCC-2026-0112', '2026-07-27T05:15:00Z'::timestamptz, '2026-07-27T05:15:00Z'::timestamptz, (SELECT p.id FROM crew.persons p WHERE p.last_name = 'Missaoui' AND p.first_name = 'R.' AND p.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid LIMIT 1), 'Missaoui R.', 'First Officer', false, true, 'TUN', 'OPERATIONAL', 'Crew fatigue declared before third consecutive night sector', 'First Officer declared unfit due to cumulative fatigue before the third consecutive night sector. Duty was within ORO.FTL.205 limits but the cumulative pattern was assessed as unsafe by the crew member.', 'Pre-flight', 'OCCURRENCE', NULL, 'C', 4, 'TOLERABLE', 'ACTIONS_OPEN', 'INCIDENT', 'Crew member removed from the duty. Standby crew activated. Sector departed 55 min late.'
UNION ALL
SELECT md5('occ:'||'OCC-2026-0111')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.occurrences', 'OCC-2026-0111', '2026-07-26T14:10:00Z'::timestamptz, '2026-07-26T14:10:00Z'::timestamptz, (SELECT p.id FROM crew.persons p WHERE p.last_name = 'Roux' AND p.first_name = 'T.' AND p.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid LIMIT 1), 'Roux T.', 'Flight Dispatcher', false, false, 'LEMD', 'OTHER', 'Temporary restricted area not reflected in the operational flight plan', 'A temporary restricted area published by NOTAM was not integrated into the OFP. Crew were advised by ATC Madrid and carried out a minor lateral deviation. No loss of separation.', 'Cruise', 'INCIDENT', 'MOR/2026/0438', 'C', 3, 'TOLERABLE', 'UNDER_REVIEW', 'INCIDENT', 'Route amended in flight. NOTAM briefing pack re-issued to all crews on the sector.'
UNION ALL
SELECT md5('occ:'||'OCC-2026-0110')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.occurrences', 'OCC-2026-0110', '2026-07-25T11:20:00Z'::timestamptz, '2026-07-25T11:20:00Z'::timestamptz, (SELECT p.id FROM crew.persons p WHERE p.last_name = 'Berriri' AND p.first_name = 'S.' AND p.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid LIMIT 1), 'Berriri S.', 'Ground Operations', false, false, 'TUN', 'FUEL', 'Fuel uplift discrepancy of 420 kg detected before departure', 'Fuel figure on the delivery note differed from the uplift recorded on the aircraft by 420 kg. Discrepancy identified during the pre-departure cross-check and corrected before engine start.', 'Pre-flight', 'OCCURRENCE', NULL, 'C', 3, 'TOLERABLE', 'RISK_ASSESSED', 'INCIDENT', 'Refuelling repeated under supervision. Fuel figure re-confirmed with the crew before block-off.'
UNION ALL
SELECT md5('occ:'||'OCC-2026-0109')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.occurrences', 'OCC-2026-0109', '2026-07-24T09:05:00Z'::timestamptz, '2026-07-24T09:05:00Z'::timestamptz, (SELECT p.id FROM crew.persons p WHERE p.last_name = 'Ayari' AND p.first_name = 'W.' AND p.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid LIMIT 1), 'Ayari W.', 'First Officer', false, false, 'LFMN', 'OTHER', 'Ground support equipment left inside the taxiway safety strip', 'A ground power unit was parked inside the hold-short line of taxiway Delta. No conflict occurred; the crew held position and advised ground control.', 'Taxi', 'OBSERVATION', NULL, 'D', 3, 'TOLERABLE', 'CLOSED', 'INCIDENT', 'Handling agent contacted; equipment repositioned within 10 minutes.'
ON CONFLICT (id) DO NOTHING;


-- Les occurrences liees, quand la reference existe
INSERT INTO safety.hazard_occurrences (hazard_id, occurrence_id)
SELECT md5('hz:'||'RSK-2026-0001')::uuid, o.id FROM safety.occurrences o WHERE o.reference = 'OCC-2026-0112' AND o.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
UNION ALL
SELECT md5('hz:'||'RSK-2026-0002')::uuid, o.id FROM safety.occurrences o WHERE o.reference = 'OCC-2026-0111' AND o.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
UNION ALL
SELECT md5('hz:'||'RSK-2026-0003')::uuid, o.id FROM safety.occurrences o WHERE o.reference = 'OCC-2026-0113' AND o.tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
ON CONFLICT DO NOTHING;

-- Le retour d experience
INSERT INTO safety.rex
    (id, tenant_id, source_type, source_ref, reference, title, category, phase,
     aircraft_type, location, narrative, recommendation, author_name, author_role,
     attribution, scope, published_on, status)
VALUES
  (md5('rex:'||'REX-2026-0018')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.rex', 'REX-2026-0018', 'Late runway change on short final at LFPG — workload trap', 'Flight Ops', 'Approach', 'Falcon 2000LX', 'LFPG', 'Cleared for 27R, ATC offered 26L at 1800 ft AAL for spacing. We accepted, and the reconfiguration consumed the whole final: FMS sequencing, new ILS, new brief. Nothing was broken, but both of us were behind the aircraft for about thirty seconds.', 'Add a runway-change item to the approach briefing: what we would accept, and below what height we would not.', 'Ben Arbia Y.', 'FALCON CAP', 'named', 'fleet', '2026-07-12'::date, 'published'),
  (md5('rex:'||'REX-2026-0017')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.rex', 'REX-2026-0017', 'Icing accretion faster than briefed climbing through FL120', 'Weather', 'Climb', 'Falcon 900LX', 'LFSB', 'SIGMET indicated moderate icing FL080–FL150. Actual accretion in the cloud layer was noticeably faster than expected, and anti-ice was selected later than ideal because we were watching OAT rather than visible moisture.', 'Include the icing band and the intended climb profile in the departure briefing whenever a SIGMET is active.', 'Cherni H.', 'FALCON CAP', 'named', 'fleet', '2026-07-04'::date, 'published'),
  (md5('rex:'||'REX-2026-0015')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.rex', 'REX-2026-0015', 'Fuel figure mismatch caught by an independent cross-check', 'Dispatch & Planning', 'Pre-flight', 'Citation CJ4', 'LFMN', 'A 200 kg discrepancy between the OFP figure and the uplift slip was found during the pre-departure cross-check. Root cause was a density value applied twice. Corrected before departure with no operational impact.', 'Make density part of the read-back on every uplift, not just on long sectors.', 'Roux T.', 'Senior Flight Dispatcher', 'named', 'fleet', '2026-06-28'::date, 'published'),
  (md5('rex:'||'REX-2026-0012')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.rex', 'REX-2026-0012', 'GPU cable left across the walkway during a quick turnaround', 'Ground Ops', 'Ground handling', 'Legacy 650', 'DTTA', 'On a 35-minute turnaround the ground power cable was routed across the passenger walkway rather than around it. No one tripped, but two passengers stepped over it. The crew noticed on the walk-around, not the ramp team.', 'Add "walkway clear" to the ramp readiness call before passengers are released to the aircraft.', 'Jaziri A.', 'Ramp Supervisor', 'named', 'department', '2026-06-15'::date, 'published'),
  (md5('rex:'||'REX-2026-0009')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.rex', 'REX-2026-0009', 'Ten hours on the ground and the trap of the "quick question"', 'Human Factors', 'N/A', NULL, 'DTTA', 'During a long OCC shift, interruptions arrived roughly every four minutes. Each was short, none was unreasonable, but the flight plan I was building took three times longer than it should have and I caught two of my own errors on the final read-through.', 'Agree a visible "do not interrupt" signal at the dispatch desk for flight plan construction.', NULL, NULL, 'anonymous', 'fleet', '2026-05-30'::date, 'published'),
  (md5('rex:'||'REX-2026-0006')::uuid, '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - SMS.db.rex', 'REX-2026-0006', 'Deferred defect that was legal, sensible on paper, and awkward in the air', 'Technical', 'Cruise', 'Legacy 650', '—', 'Departed with an APU deferred under a category B item. Entirely legal and correctly documented. On the ground at destination with no ground power available, the turnaround took an extra forty minutes and the cabin was uncomfortable for the passengers.', 'Add a destination-impact line to the MEL acceptance check: what does this deferral cost where we are going?', 'Haddad J.', 'Post Holder — Continuing Airworthiness (CAMO)', 'named', 'fleet', '2026-05-18'::date, 'published');

-- Les lecons
INSERT INTO safety.rex_lessons (rex_id, lesson, sort_order) VALUES
  (md5('rex:'||'REX-2026-0018')::uuid, 'A late runway change is a configuration change AND a briefing change — treat it as both.', 1),
  (md5('rex:'||'REX-2026-0018')::uuid, 'Below 2000 ft AAL the default answer to a runway swap should be "unable" unless it was already briefed.', 2),
  (md5('rex:'||'REX-2026-0018')::uuid, 'The pilot monitoring should call the remaining actions aloud instead of silently catching up on the FMS.', 3),
  (md5('rex:'||'REX-2026-0017')::uuid, 'Visible moisture plus TAT in range is the trigger — not the ice you can see on the wiper bolt.', 1),
  (md5('rex:'||'REX-2026-0017')::uuid, 'Brief the anti-ice altitude band out loud during the departure brief, with a target flight level.', 2),
  (md5('rex:'||'REX-2026-0017')::uuid, 'Request a climb rate that minimises time in the icing layer instead of accepting a slow step climb.', 3),
  (md5('rex:'||'REX-2026-0015')::uuid, 'The independent cross-check works — but only when it is done from the source document, not from the previous figure.', 1),
  (md5('rex:'||'REX-2026-0015')::uuid, 'Read the density back with the uplift figure, the same way a clearance is read back.', 2),
  (md5('rex:'||'REX-2026-0015')::uuid, 'A round discrepancy is more suspicious than an odd one: it usually means a factor applied twice.', 3),
  (md5('rex:'||'REX-2026-0012')::uuid, 'Under time pressure the cable goes where it is quickest, not where it is safest.', 1),
  (md5('rex:'||'REX-2026-0012')::uuid, 'The walkway route should be set before the passengers are called, not after.', 2),
  (md5('rex:'||'REX-2026-0012')::uuid, 'A trip hazard is invisible to the person who put it there — a second pair of eyes finds it.', 3),
  (md5('rex:'||'REX-2026-0009')::uuid, 'Interruption cost is not the length of the interruption, it is the time to get back to where you were.', 1),
  (md5('rex:'||'REX-2026-0009')::uuid, 'Say "give me two minutes" — it is not rude, it is the safer answer.', 2),
  (md5('rex:'||'REX-2026-0009')::uuid, 'Finish the block you are in before answering, and note where you were if you cannot.', 3),
  (md5('rex:'||'REX-2026-0006')::uuid, 'A deferred item that is legal at departure can still be operationally expensive at destination.', 1),
  (md5('rex:'||'REX-2026-0006')::uuid, 'Check what the deferred system is actually used for at the destination, not just whether the MEL allows it.', 2),
  (md5('rex:'||'REX-2026-0006')::uuid, 'Tell the handling agent about the deferral in advance — ground power can usually be arranged if asked early.', 3);

-- Les notifications
INSERT INTO safety.notifications
    (tenant_id, at, kind, title, body, severity, entity_ref, domain, read_at)
VALUES
  ('00000000-0000-0000-0000-000000000001'::uuid, '2026-07-27T06:42:00Z'::timestamptz, 'occurrence', 'New occurrence — OCC-2026-0113', 'Bird strike on final approach — LFPG RWY 27R. Classified serious incident, MOR filed.', 'critical', 'OCC-2026-0113', 'OPS', NULL),
  ('00000000-0000-0000-0000-000000000001'::uuid, '2026-07-27T05:15:00Z'::timestamptz, 'occurrence', 'Confidential fatigue report — OCC-2026-0112', 'First Officer declared unfit before a third consecutive night sector. Handled under the FRMS.', 'high', 'OCC-2026-0112', 'CREW', NULL),
  ('00000000-0000-0000-0000-000000000001'::uuid, '2026-07-16T08:00:00Z'::timestamptz, 'capa', 'Corrective action overdue — CAPA-2026-0003', 'SMS familiarisation training for ground operations staff was due 15 Jul 2026.', 'critical', 'CAPA-2026-0003', 'TRAINING', NULL),
  ('00000000-0000-0000-0000-000000000001'::uuid, '2026-07-20T09:00:00Z'::timestamptz, 'audit', 'Audit in progress — AUD-2026-03', 'Safety risk management process audit against ICAO Annex 19 component 2. Three findings still open.', 'medium', 'AUD-2026-03', 'ORG', '2026-07-20T09:00:00Z'::timestamptz),
  ('00000000-0000-0000-0000-000000000001'::uuid, '2026-07-27T10:00:00Z'::timestamptz, 'promotion', 'Safety alert issued — SB-2026-0004', 'Wildlife activity at LFPG during early morning operations. 9 of 24 acknowledgements received.', 'info', 'SB-2026-0004', 'OPS', '2026-07-27T10:00:00Z'::timestamptz);
