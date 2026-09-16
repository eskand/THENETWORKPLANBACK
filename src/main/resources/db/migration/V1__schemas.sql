-- ============================================================
--  V1 — one schema per domain (annexe A2, § domains)
--  platform   DOM9  tenants, users, audit
--  refdata    DOM8  airports, aircraft types
--  camo       DOM5  aircraft, MEL
--  ops        DOM1  trips, legs, releases, delays, alerts
--  crew       DOM4  persons, leg assignments
--  tripsupport DOM2 country status, permits, ground services
--  planning   DOM3  reserved (fuel, W&B, FPL)
--  safety     DOM6  reserved (occurrences, risks, ERP)
--  sales      DOM7  reserved (requests, quotes)
-- ============================================================
CREATE SCHEMA IF NOT EXISTS platform;
CREATE SCHEMA IF NOT EXISTS refdata;
CREATE SCHEMA IF NOT EXISTS camo;
CREATE SCHEMA IF NOT EXISTS ops;
CREATE SCHEMA IF NOT EXISTS crew;
CREATE SCHEMA IF NOT EXISTS tripsupport;
CREATE SCHEMA IF NOT EXISTS planning;
CREATE SCHEMA IF NOT EXISTS safety;
CREATE SCHEMA IF NOT EXISTS sales;

COMMENT ON SCHEMA ops IS 'DOM1 Flight Ops — the leg is the root aggregate';
COMMENT ON SCHEMA tripsupport IS 'DOM2 Trip Support — the right to fly the leg';
COMMENT ON SCHEMA crew IS 'DOM4 Crew';
COMMENT ON SCHEMA camo IS 'DOM5 Airworthiness';
COMMENT ON SCHEMA refdata IS 'DOM8 Reference Data';
COMMENT ON SCHEMA platform IS 'DOM9 Platform';
