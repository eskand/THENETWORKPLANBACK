-- ============================================================
--  V54 — le catalogue de rapports de l'annexe A4.
--
--  POURQUOI. Le catalogue en tenait dix ; l'annexe A4 en tient
--  vingt-sept, plus le constructeur de rapport sur mesure. Les
--  dix-sept qui manquaient ne sont pas des variantes : ce sont les
--  questions qu'un exploitant pose reellement — combien de secteurs
--  par jour, quelles destinations, quel equipage a vole combien,
--  qu'est-ce qui a ete annule, quelle ligne de service depasse une
--  limite FTL.
--
--  CE QUI S'AJOUTE A LA DEFINITION. Le module (Flights, Crew, Sales,
--  Maintenance, Safety (SMS)) parce que le menu de gauche les regroupe
--  ainsi et dans cet ordre-la ; le sous-titre, qui dit ce a quoi le
--  rapport repond ; et la portee, qui dit comment la flotte et la
--  periode s'y appliquent. Sans la portee, deux rapports voisins
--  semblent se contredire alors qu'ils ne filtrent simplement pas la
--  meme chose — « Crew Members » lit la periode comme une fenetre
--  d'echeance, « Crew Days » la lit comme des jours rostered.
--
--  LES DIX DEJA PRESENTS SONT REECRITS, PAS DOUBLES. Leur titre etait
--  celui du sprint qui les avait poses (« Fleet utilisation »), pas
--  celui du prototype approuve (« Acft Block Time by Month »). Un
--  deuxieme code pour la meme question aurait laisse deux entrees
--  quasi identiques dans le menu, et l'operateur aurait eu a deviner
--  laquelle citer.
--
--  LE CODE RESTE LA CLE. Un runner Java s'enregistre sur lui. Une
--  definition sans runner s'affiche et refuse de s'executer, ce qui
--  est preferable a un rapport qui rend un tableau vide sans dire
--  pourquoi.
-- ============================================================

ALTER TABLE platform.report_definitions
    ADD COLUMN module   text,
    ADD COLUMN subtitle text,
    ADD COLUMN scope    text;

-- ── les vingt-sept du prototype ──────────────────────────────
--  Insere ce qui manque, reecrit ce qui existait : le code est la
--  cle naturelle, et `uq_report_code` la porte.
INSERT INTO platform.report_definitions
    (id, tenant_id, code, title, domain, module, subtitle, scope, description,
     default_window_days, source_type, source_ref)
SELECT md5('rep-' || r.code)::uuid,
       '00000000-0000-0000-0000-000000000001'::uuid,
       r.code, r.title, r.domain, r.module, r.subtitle, r.scope, r.description,
       r.window_days, 'seed', 'annexe A4 - catalogue de rapports'
  FROM (VALUES
    -- ── Flights ──────────────────────────────────────────────
    ('OPS-UTIL', 'Acft Block Time by Month', 'OPS', 'Flights',
     'Block time, sectors and productivity per tail — with monthly breakdown',
     'Fleet + period applied to sectors',
     'One line per tail: sectors flown, block hours, average sector and share of the fleet total.', 90),

    ('OPS-OTP', 'On-Time Performance & Delays', 'OPS', 'Flights',
     'OTP, delay minutes and root-cause Pareto from recorded OCC delays',
     'Fleet + period applied to sectors',
     'Punctuality on the D15 convention, with every delayed sector and the cause recorded against it.', 30),

    ('OPS-ROUTE', 'Route Statistics', 'OPS', 'Flights',
     'Airports served, route frequency, stage length and regional split',
     'Fleet + period applied to sectors',
     'A directed city pair per line: the two directions are two sectors.', 90),

    ('OPS-LOG', 'Aircraft Flights', 'OPS', 'Flights',
     'Complete operated leg list for the period — the exportable ops journal',
     'Fleet + period applied to sectors',
     'The sector-level source table behind every other operations report.', 30),

    ('OPS-SUM', 'Sum of Flights', 'OPS', 'Flights',
     'Totals per aircraft, type and month — flights, block time and airborne time',
     'Fleet + period applied to sectors',
     'One line per day on which something flew, with the block time and the load.', 30),

    ('OPS-SCHED', 'Schedule', 'OPS', 'Flights',
     'Day-by-day published schedule, one line per sector with times and aircraft',
     'Fleet + period applied to sectors',
     'The programme with STD/STA against the recorded OUT and IN.', 7),

    ('OPS-STATUS', 'Flights Status', 'OPS', 'Flights',
     'Where every sector stands: scheduled, airborne, completed, delayed or cancelled',
     'Fleet + period applied to sectors',
     'Sectors grouped by the state they finished in.', 30),

    ('OPS-CXL', 'Cancelled Flights', 'OPS', 'Flights',
     'Cancelled sectors with reason, aircraft and lost block time',
     'Fleet + period applied to sectors',
     'Cancellations with their remark and the passengers affected.', 30),

    ('OPS-DEST', 'Top Destinations', 'OPS', 'Flights',
     'Most-served arrival airports with block time, region and aircraft mix',
     'Fleet + period applied to sectors',
     'Movements per aerodrome, arrivals and departures counted separately.', 90),

    ('OPS-TOP100', 'Top 100 Routes', 'OPS', 'Flights',
     'Ranked route table by frequency, block time and punctuality',
     'Fleet + period applied to sectors',
     'The hundred busiest city pairs, ranked, with the punctuality of each.', 90),

    ('OPS-PAX', 'PAX by Route', 'OPS', 'Flights',
     'Passengers carried per route, load factor and check-in completeness',
     'Fleet + period applied to sectors',
     'Passengers per route, with the empty sectors kept in the count.', 90),

    -- ── Crew ─────────────────────────────────────────────────
    ('CREW-EXP', 'Crew Currency', 'CREW', 'Crew',
     'Licence, medical and crew-status validity horizon',
     'Fleet by type rating + period as the expiry window',
     'Every document that expires inside the window, soonest first.', 90),

    ('CREW-TRAIN', 'Crew Currency Summary', 'CREW', 'Crew',
     'Recurrent qualifications, expiry pipeline and simulator currency',
     'Fleet by type rating + period as the expiry window',
     'One line per crew member: the three documents and the one that expires first.', 90),

    ('CREW-FUNC', 'Block Time by Function', 'CREW', 'Crew',
     'Block hours split by operating function — PIC, SIC and cabin crew',
     'Fleet + period applied to crewed sectors',
     'Commander, co-pilot and cabin: three different currencies.', 90),

    ('CREW-BLOCK', 'Crew Block Time', 'CREW', 'Crew',
     'Block hours per crew member with rolling 28-day exposure',
     'Fleet + period applied to crewed sectors',
     'Block time per person, from the duty periods that record flying.', 90),

    ('CREW-DAYS', 'Crew Days', 'CREW', 'Crew',
     'Duty days, days off and standby split per crew member',
     'Fleet by type rating + period on rostered days',
     'What each crew member actually did with the period.', 30),

    ('CREW-MEMBERS', 'Crew Members', 'CREW', 'Crew',
     'Crew directory — ratings, base, seniority and contact record',
     'Fleet by type rating + period as the expiry window',
     'Every crew member with licence, medical and recurrent training.', 90),

    ('CREW-STAFF', 'Crew Staffing Plan', 'CREW', 'Crew',
     'Crew required by the flown programme against crew available per fleet',
     'Fleet + period applied to sectors and crew supply',
     'What the programme asked of each fleet, and who was rated to answer it.', 30),

    ('CREW-DUTY', 'Crew Duty', 'CREW', 'Crew',
     'Every rostered duty with report time, duty end and duration',
     'Fleet by type rating + period on rostered days',
     'Duty and rest as recorded, one line per duty period.', 30),

    ('CREW-ROSTER', 'Roster and Duty', 'CREW', 'Crew',
     'Published roster coverage day by day, with duty and rest pattern per crew',
     'Fleet by type rating + period on rostered days',
     'One line per crew member: working days, days off, standby and block flown.', 30),

    ('CREW-FTL', 'FTL Sheet', 'CREW', 'Crew',
     'Duty against maximum FDP per ORO.FTL.205 with rolling limit exposure',
     'Fleet by type rating + period on rostered days',
     'Cumulative block and duty against the 100 h / 28 d and 900 h / year ceilings.', 90),

    ('CREW-FTLV', 'FTL Violations', 'CREW', 'Crew',
     'Duties exceeding a flight-time-limitation threshold, with the rule breached',
     'Fleet by type rating + period on rostered days',
     'Exceedances only. An empty report is the correct result.', 90),

    -- ── Sales ────────────────────────────────────────────────
    ('FLEET-REG', 'Aircraft Availability', 'COMMERCIAL', 'Sales',
     'Tail-by-tail status, type mix and technical availability for sales',
     'Fleet applied; period drives the sectors-flown column',
     'Registration, type, status and what it flew in the period.', 90),

    ('COM-PIPE', 'Commercial Pipeline', 'COMMERCIAL', 'Sales',
     'Clients, quotes and feasibility requests',
     'Period + fleet on quotes; the client directory narrows only through them',
     'Every quote raised in the window with its stage and value.', 90),

    -- ── Maintenance ──────────────────────────────────────────
    ('MX-DEFECTS', 'Tech Log — Defects & Deferrals', 'MAINTENANCE', 'Maintenance',
     'Open, deferred (MEL) and closed defects, by system and aircraft',
     'Fleet + period on the date the defect was raised',
     'The defect record with its ATA chapter and how it was closed.', 90),

    ('MX-ARC', 'CAMO — Airworthiness Status', 'MAINTENANCE', 'Maintenance',
     'ARC validity, AD/SB status, life-limited parts and next checks',
     'Fleet applied; period is the expiry / due window',
     'What expires, what is overdue, and on which registration.', 90),

    -- ── Safety (SMS) ─────────────────────────────────────────
    ('SAF-OCC', 'Safety Reports & Occurrences', 'SAFETY', 'Safety (SMS)',
     'Severity, status and type breakdown of SMS reports',
     'Fleet where the flight is identifiable; SMS entries carry a relative age, not a date',
     'What was reported in the period, how severe it was and where it stands.', 90)
  ) AS r(code, title, domain, module, subtitle, scope, description, window_days)
ON CONFLICT (tenant_id, code) DO UPDATE
   SET title               = excluded.title,
       domain              = excluded.domain,
       module              = excluded.module,
       subtitle            = excluded.subtitle,
       scope               = excluded.scope,
       description         = excluded.description,
       default_window_days = excluded.default_window_days,
       updated_at          = now();

-- ── les rapports propres au produit ──────────────────────────
--  Quatre rapports que le prototype n'a pas et que l'application
--  repond deja : la liste des echeances de maintenance, les MEL en
--  vigueur et les permis en attente. Ils restent au catalogue —
--  les retirer supprimerait une reponse que quelqu'un utilise — mais
--  ils portent leur propre module pour qu'on les distingue du
--  perimetre approuve.
UPDATE platform.report_definitions
   SET module   = coalesce(module, CASE domain
                      WHEN 'OPS' THEN 'Flights'
                      WHEN 'CREW' THEN 'Crew'
                      WHEN 'MAINTENANCE' THEN 'Maintenance'
                      WHEN 'COMMERCIAL' THEN 'Sales'
                      WHEN 'SAFETY' THEN 'Safety (SMS)'
                      ELSE 'Trip support' END),
       subtitle = coalesce(subtitle, description)
 WHERE module IS NULL;

UPDATE platform.report_definitions
   SET scope = 'Fleet + period applied to sectors'
 WHERE code = 'OPS-DELAY' AND scope IS NULL;

UPDATE platform.report_definitions
   SET scope = 'Fleet applied; period is the due window'
 WHERE code IN ('MX-DUE', 'MX-MEL') AND scope IS NULL;

UPDATE platform.report_definitions
   SET scope = 'Period applied to the permit request date'
 WHERE code = 'TS-PERMITS' AND scope IS NULL;
