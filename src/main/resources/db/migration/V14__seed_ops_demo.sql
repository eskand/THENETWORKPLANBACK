-- ============================================================
--  V14 — jeu de données Opérations : pistes, notes d'aérodrome,
--  fournisseurs, positions.
--
--  Les positions ne sont semées que pour les étapes réellement
--  parties (2 dans V8) : une étape au sol n'a pas de position,
--  et l'écran doit le dire au lieu d'en inventer une.
-- ============================================================

-- ------------------------------------------------------------
--  1. Pistes. La longueur vient de la longueur publiée déjà
--     portée par refdata.airports : on ne réinvente pas la donnée,
--     on la structure.
-- ------------------------------------------------------------
INSERT INTO refdata.runways (id, airport_id, designator, length_ft, width_ft, surface,
                             lda_ft, toda_ft, ils_category, lighting, source_type, source_ref)
SELECT md5('rwy-' || a.icao || '-' || r.designator)::uuid,
       a.id,
       r.designator,
       CASE WHEN r.primary_runway THEN a.longest_runway_ft
            ELSE GREATEST(4000, (a.longest_runway_ft * 0.78)::int) END,
       CASE WHEN a.longest_runway_ft > 10000 THEN 150 ELSE 148 END,
       'ASPH',
       CASE WHEN r.primary_runway THEN a.longest_runway_ft
            ELSE GREATEST(4000, (a.longest_runway_ft * 0.78)::int) END,
       CASE WHEN r.primary_runway THEN a.longest_runway_ft + 500
            ELSE GREATEST(4000, (a.longest_runway_ft * 0.78)::int) + 400 END,
       CASE WHEN a.aerodrome_category = 'A' AND r.primary_runway THEN 'CAT_II'
            WHEN a.aerodrome_category = 'A' THEN 'CAT_I'
            WHEN a.aerodrome_category = 'B' THEN 'CAT_I'
            ELSE 'NONE' END,
       CASE WHEN a.aerodrome_category = 'A' THEN 'HIRL, CL, ALS' ELSE 'MIRL' END,
       'seed', 'AIP'
FROM refdata.airports a
CROSS JOIN (VALUES ('01/19', true), ('11/29', false)) AS r(designator, primary_runway)
WHERE a.longest_runway_ft IS NOT NULL;

-- ------------------------------------------------------------
--  2. Notes d'aérodrome : PPR, couvre-feu, créneaux, douane.
--     Elles portent une fenêtre de validité et une provenance.
-- ------------------------------------------------------------
INSERT INTO refdata.airport_notes (id, airport_id, kind, title, detail, valid_from, valid_to, severity,
                                   source_type, source_ref)
SELECT md5('note-' || a.icao || '-' || n.kind)::uuid,
       a.id, n.kind, n.title, n.detail,
       (date_trunc('day', now()) - interval '60 days')::date,
       CASE WHEN n.temporary THEN (date_trunc('day', now()) + interval '45 days')::date END,
       n.severity,
       'seed', 'AIP / operator file'
FROM refdata.airports a
JOIN (VALUES
    ('LFPB', 'CURFEW',      'Night curfew 22:00-06:00 LT',            'Movements prohibited except medical or state flights.', 'ATTENTION', false),
    ('LFPB', 'SLOT',        'Slot required, business aviation',        'Slot to be requested through the handler, 24 h ahead.', 'INFO', false),
    ('EGGW', 'PPR',         'PPR required for non-based aircraft',     'PPR number to be quoted in the flight plan remarks.',   'ATTENTION', false),
    ('EGLL', 'SLOT',        'Slot coordinated aerodrome, level 3',     'ACL slot mandatory. No slot, no flight plan.',          'CRITICAL', false),
    ('DNMM', 'CUSTOMS',     'Customs on request only',                 'Notify 48 h ahead through the handler.',                'ATTENTION', false),
    ('DNMM', 'RESTRICTION', 'Landing permit mandatory',                'Overflight and landing permit required for all non-scheduled flights.', 'CRITICAL', false),
    ('HLLT', 'RESTRICTION', 'Operations subject to state approval',    'Verify the current security situation before dispatch.', 'CRITICAL', false),
    ('GOBD', 'FUEL',        'Jet A1 available, payment on account',    'Fuel release required before uplift.',                  'INFO', false),
    ('LSGG', 'SLOT',        'Slot required in peak periods',           'Business aviation terminal, separate slot pool.',       'INFO', false),
    ('DTTA', 'HANDLING',    'Home base handling by the operator',      'No third-party handler required.',                      'INFO', false),
    ('LFML', 'NOTE',        'Maintenance base, 2C capability',         'Line and base maintenance available.',                  'INFO', false),
    ('OMDB', 'SLOT',        'Slot coordinated, general aviation apron','Parking limited to 4 hours without prior arrangement.', 'ATTENTION', true)
) AS n(icao, kind, title, detail, severity, temporary) ON n.icao = a.icao;

-- ------------------------------------------------------------
--  3. Fournisseurs par escale.
--     NetPlus Services est proposé partout, en tête de liste :
--     c'est le guichet interne, comme dans le prototype.
-- ------------------------------------------------------------
INSERT INTO tripsupport.suppliers (id, tenant_id, station_icao, service_type, name, email, phone, sita,
                                   contract_ref, preferred, lead_time_hours, source_type, source_ref)
SELECT md5('sup-nps-' || a.icao || '-' || s.service_type)::uuid,
       '00000000-0000-0000-0000-000000000001',
       a.icao,
       s.service_type,
       'NetPlus Services',
       'services@thenetworkplan.com',
       '+216 71 000 000',
       NULL,
       'TNP-INTERNAL',
       true,
       s.lead_time,
       'seed', 'Operator desk'
FROM refdata.airports a
CROSS JOIN (VALUES
    ('HANDLING', 4), ('FUEL', 6), ('CATERING', 12), ('CREW_TRANSPORT', 4),
    ('PAX_TRANSPORT', 6), ('CUSTOMS', 24), ('GAR', 24), ('APIS', 24)
) AS s(service_type, lead_time);

-- Un fournisseur local en second sur chaque escale, pour que le choix existe.
INSERT INTO tripsupport.suppliers (id, tenant_id, station_icao, service_type, name, email, phone, sita,
                                   contract_ref, preferred, lead_time_hours, source_type, source_ref)
SELECT md5('sup-local-' || a.icao || '-' || s.service_type)::uuid,
       '00000000-0000-0000-0000-000000000001',
       a.icao,
       s.service_type,
       CASE s.service_type
            WHEN 'HANDLING' THEN a.city || ' Executive Handling'
            WHEN 'FUEL' THEN 'Into-plane ' || a.iata
            ELSE a.city || ' ' || initcap(replace(s.service_type, '_', ' ')) END,
       'ops@' || lower(a.iata) || '-handling.example',
       '+00 000 000 000',
       upper(a.iata) || 'KKXH',
       'FRAME-' || upper(a.iata),
       false,
       s.lead_time,
       'seed', 'Supplier contract'
FROM refdata.airports a
CROSS JOIN (VALUES ('HANDLING', 4), ('FUEL', 6), ('CATERING', 12)) AS s(service_type, lead_time)
WHERE a.iata IS NOT NULL;

-- ------------------------------------------------------------
--  4. Positions : seulement pour les étapes en vol.
--     Une position toutes les cinq minutes depuis le décollage,
--     interpolée entre les deux terrains publiés — donc marquée
--     provider = 'MANUAL' et source 'seed', jamais 'ADSB'.
-- ------------------------------------------------------------
INSERT INTO ops.position_reports (id, tenant_id, leg_id, aircraft_id, reported_at, received_at,
                                  latitude, longitude, altitude_ft, ground_speed_kt, track_deg,
                                  vertical_rate_fpm, on_ground, provider, provider_ref,
                                  source_type, source_ref)
SELECT md5('pos-' || l.id::text || '-' || p.n::text)::uuid,
       l.tenant_id,
       l.id,
       l.aircraft_id,
       COALESCE(l.off_at, l.out_at, l.std) + (p.n * interval '5 minutes'),
       COALESCE(l.off_at, l.out_at, l.std) + (p.n * interval '5 minutes') + interval '8 seconds',
       dep.latitude + (arr.latitude - dep.latitude) * (p.n::numeric / 12),
       dep.longitude + (arr.longitude - dep.longitude) * (p.n::numeric / 12),
       CASE WHEN p.n <= 3 THEN p.n * 9000 ELSE 37000 END,
       CASE WHEN p.n <= 2 THEN 280 + p.n * 40 ELSE 445 END,
       ((degrees(atan2(arr.longitude - dep.longitude, arr.latitude - dep.latitude))::int % 360) + 360) % 360,
       CASE WHEN p.n <= 3 THEN 2200 ELSE 0 END,
       false,
       'MANUAL',
       'Seeded track, not a received signal',
       'seed', 'Demo track'
FROM ops.legs l
JOIN refdata.airports dep ON dep.icao = l.dep_icao
JOIN refdata.airports arr ON arr.icao = l.arr_icao
CROSS JOIN generate_series(1, 12) AS p(n)
WHERE l.status = 'DEPARTED'
  AND COALESCE(l.off_at, l.out_at, l.std) + (p.n * interval '5 minutes') <= now();
