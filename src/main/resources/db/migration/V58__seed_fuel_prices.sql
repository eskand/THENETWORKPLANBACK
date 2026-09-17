-- ============================================================
--  V58 — Tarifs carburant de demonstration
--
--  Un exploitant importe sa liste de tarifs (l'annexe le fait en
--  CSV, par fournisseur). La demonstration n'en a pas, et l'onglet
--  FUEL du dossier de vol afficherait « NO DATA » sur chaque etape.
--
--  Le tarif est pose sur les fournisseurs de carburant DEJA dans
--  l'annuaire, jamais sur des escales inventees : une ligne de prix
--  sans fournisseur en face n'aurait designe personne. La valeur est
--  tiree dans la meme fourchette que l'annexe (4,20 a 6,50 USD/USG,
--  prototype l. 14306), de facon deterministe — le meme aerodrome
--  donne le meme prix a chaque execution de la migration, ce qu'un
--  random() ne garantirait pas.
--
--  source_type = 'seed' : ces lignes se distinguent d'un import
--  reel, et se suppriment d'une clause.
-- ============================================================
INSERT INTO tripsupport.fuel_prices (
    tenant_id, source_type, station_icao, supplier_name, fuel_grade,
    price, unit, currency, effective_from, effective_to)
SELECT
    s.tenant_id,
    'seed',
    s.station_icao,
    s.name,
    'JET A-1',
    round((4.20 + (('x' || substr(md5(s.station_icao || s.name), 1, 8))::bit(32)::bigint
                   & 2147483647) % 231 / 100.0)::numeric, 4),
    'USG',
    'USD',
    CURRENT_DATE - 30,
    NULL
FROM tripsupport.suppliers s
WHERE s.service_type = 'FUEL'
  AND s.active
ON CONFLICT ON CONSTRAINT uq_fuel_price DO NOTHING;
