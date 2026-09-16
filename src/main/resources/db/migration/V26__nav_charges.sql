-- ============================================================
--  V26 — redevances de navigation en route.
--
--  Deux fournisseurs, deux formules différentes, et c'est tout
--  l'intérêt : EUROCONTROL facture par zone traversée, ASECNA une
--  seule fois sur la distance totale cumulée de ses FIR. Une
--  formule unique aurait forcé l'une des deux à mentir — d'où une
--  architecture à fournisseurs, reprise telle quelle de l'annexe.
--
--  Les taux ont une période de validité. EUROCONTROL republie les
--  siens chaque mois ; un taux périmé donne une facture fausse.
--  L'écran affiche donc la période à côté du montant, comme il
--  affiche la date de référence à côté d'un préavis de permis.
--
--  Un taux NULL n'est pas zéro : c'est une zone que le fournisseur
--  couvre mais dont le taux courant n'a pas été sourcé. Le calcul
--  répond alors « non implémenté » au lieu de deviner. Le Maroc et
--  l'Égypte sont exactement dans ce cas : facturés par EUROCONTROL
--  sous « Air Navigation Charges » (Customer Guide, partie C), mais
--  les documents fournis ne publient pas leur taux courant.
-- ============================================================

CREATE TABLE refdata.nav_charge_zones (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    source_type     text NOT NULL DEFAULT 'refdata',
    source_ref      text,
    source_version  text,
    source_author   uuid,
    source_at       timestamptz NOT NULL DEFAULT now(),

    provider        text NOT NULL,
    zone_code       text NOT NULL,
    country_label   text NOT NULL,
    unit_rate_eur   numeric(10,2),
    effective_from  date NOT NULL,
    effective_to    date,

    CONSTRAINT uq_nav_zone UNIQUE (provider, zone_code, effective_from)
);

COMMENT ON COLUMN refdata.nav_charge_zones.unit_rate_eur IS
    'Taux unitaire publié. NULL quand la zone est couverte par le fournisseur '
    'mais que son taux n''a pas encore été sourcé : le calcul répond alors '
    '« non implémenté » au lieu de deviner.';

-- La zone tarifaire se déduit des deux premières lettres de l'OACI de la FIR.
-- Deux exceptions documentées par EUROCONTROL, portées en table plutôt qu'en
-- dur dans le code : une exception écrite dans une fonction est une exception
-- que personne ne retrouve.
CREATE TABLE refdata.fir_charge_zone_overrides (
    fir_code    text PRIMARY KEY,
    provider    text NOT NULL,
    zone_code   text NOT NULL,
    source_ref  text
);

-- Les FIR desservies par un fournisseur qui facture globalement (ASECNA).
CREATE TABLE refdata.provider_firs (
    provider    text NOT NULL,
    fir_code    text NOT NULL,
    source_ref  text,
    PRIMARY KEY (provider, fir_code)
);

INSERT INTO refdata.nav_charge_zones
    (id, source_ref, source_version, provider, zone_code, country_label,
     unit_rate_eur, effective_from, effective_to)
SELECT r.id, 'EUROCONTROL CRCO — Adjusted unit rates applicable to May 2026 flights',
       '2026-05', 'EUROCONTROL', r.zone, r.country, r.rate,
       DATE '2026-05-01', DATE '2026-05-31'
FROM (VALUES
  (md5('navzone-EC-AZ')::uuid, 'AZ', 'Portugal (Santa Maria)', 8.16),
  (md5('navzone-EC-EB')::uuid, 'EB', 'Belgium/Luxembourg', 116.65),
  (md5('navzone-EC-ED')::uuid, 'ED', 'Germany', 97.89),
  (md5('navzone-EC-EE')::uuid, 'EE', 'Estonia', 88.97),
  (md5('navzone-EC-EF')::uuid, 'EF', 'Finland', 90.4),
  (md5('navzone-EC-EG')::uuid, 'EG', 'United Kingdom', 88.26),
  (md5('navzone-EC-EH')::uuid, 'EH', 'Netherlands', 136.38),
  (md5('navzone-EC-EI')::uuid, 'EI', 'Ireland', 34.7),
  (md5('navzone-EC-EK')::uuid, 'EK', 'Denmark', 91.64),
  (md5('navzone-EC-EN')::uuid, 'EN', 'Norway', 60.99),
  (md5('navzone-EC-EP')::uuid, 'EP', 'Poland', 98.55),
  (md5('navzone-EC-ES')::uuid, 'ES', 'Sweden', 90.61),
  (md5('navzone-EC-EV')::uuid, 'EV', 'Latvia', 55.89),
  (md5('navzone-EC-EY')::uuid, 'EY', 'Lithuania', 60.74),
  (md5('navzone-EC-GC')::uuid, 'GC', 'Spain (Canarias)', 53.4),
  (md5('navzone-EC-LA')::uuid, 'LA', 'Albania', 43.37),
  (md5('navzone-EC-LB')::uuid, 'LB', 'Bulgaria', 31.97),
  (md5('navzone-EC-LC')::uuid, 'LC', 'Cyprus', 40.89),
  (md5('navzone-EC-LD')::uuid, 'LD', 'Croatia', 39.19),
  (md5('navzone-EC-LE')::uuid, 'LE', 'Spain (Continental)', 71.3),
  (md5('navzone-EC-LF')::uuid, 'LF', 'France', 79.58),
  (md5('navzone-EC-LG')::uuid, 'LG', 'Greece', 22.39),
  (md5('navzone-EC-LH')::uuid, 'LH', 'Hungary', 40.98),
  (md5('navzone-EC-LI')::uuid, 'LI', 'Italy', 73.71),
  (md5('navzone-EC-LJ')::uuid, 'LJ', 'Slovenia', 65.42),
  (md5('navzone-EC-LK')::uuid, 'LK', 'Czech Republic', 78.89),
  (md5('navzone-EC-LM')::uuid, 'LM', 'Malta', 18.6),
  (md5('navzone-EC-LO')::uuid, 'LO', 'Austria', 66.02),
  (md5('navzone-EC-LP')::uuid, 'LP', 'Portugal (Lisboa)', 41.85),
  (md5('navzone-EC-LQ')::uuid, 'LQ', 'Bosnia-Herzegovina', 25.4),
  (md5('navzone-EC-LR')::uuid, 'LR', 'Romania', 50.17),
  (md5('navzone-EC-LS')::uuid, 'LS', 'Switzerland', 171.07),
  (md5('navzone-EC-LT')::uuid, 'LT', 'Turkey', 39.78),
  (md5('navzone-EC-LU')::uuid, 'LU', 'Moldova', 194.96),
  (md5('navzone-EC-LW')::uuid, 'LW', 'North Macedonia', 43.79),
  (md5('navzone-EC-LY')::uuid, 'LY', 'Serbia/Montenegro/KFOR', 38.85),
  (md5('navzone-EC-LZ')::uuid, 'LZ', 'Slovak Republic', 76.22),
  (md5('navzone-EC-U1')::uuid, 'U1', 'Ukraine (South)', 14.52),
  (md5('navzone-EC-UD')::uuid, 'UD', 'Armenia', 32.92),
  (md5('navzone-EC-UG')::uuid, 'UG', 'Georgia', 20.8),
  (md5('navzone-EC-UK')::uuid, 'UK', 'Ukraine', 36.88)) AS r(id, zone, country, rate);

INSERT INTO refdata.fir_charge_zone_overrides (fir_code, provider, zone_code, source_ref) VALUES
  ('LPPO', 'EUROCONTROL', 'AZ', 'EUROCONTROL Conditions of Application, Annexe 1'),
  ('LPPC', 'EUROCONTROL', 'LP', 'EUROCONTROL Conditions of Application, Annexe 1');

INSERT INTO refdata.provider_firs (provider, fir_code, source_ref) VALUES
  ('ASECNA', 'DBBB', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'DFFF', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'FKKK', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'FEFF', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'FTTT', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'FCCC', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'DIAP', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'FOOL', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'FGSL', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'GGGG', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'GOOO', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'DRRR', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026'),
  ('ASECNA', 'XBEN', 'ASECNA — Redevance d''usage des aides et services en route, eff. 1 Jan 2026');
