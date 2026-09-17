-- ============================================================
--  V56 — Flight file: the service types the flight label offers
--
--  The A4 annexe's SERVICES tab offers eleven service types
--  (prototype l. 12644, SERVICE_TYPES_BASE) plus the two the
--  United Kingdom stations add (GAR, FCP). V7 stored nine of
--  them, so "Parking / Stand", "PPR", "Airport Slot" and
--  "Landing Permit" could be picked in the annexe and not saved
--  here. The check constraint is widened rather than dropped:
--  an unknown service type must still be refused by the
--  database, not only by the enum.
-- ============================================================
ALTER TABLE tripsupport.service_requests
    DROP CONSTRAINT ck_service_type;

ALTER TABLE tripsupport.service_requests
    ADD CONSTRAINT ck_service_type CHECK (service_type IN (
        'HANDLING', 'FUEL', 'CATERING', 'CREW_TRANSPORT', 'PAX_TRANSPORT',
        'CUSTOMS', 'DEICING', 'GAR', 'APIS',
        'PARKING', 'PPR', 'AIRPORT_SLOT', 'LANDING_PERMIT', 'FCP'));

-- The supplier directory is keyed by the same vocabulary: a station that has a
-- de-icing contract must be able to have a PPR contact too, or the SERVICES
-- row for PPR would have no supplier to offer.
ALTER TABLE tripsupport.suppliers
    DROP CONSTRAINT ck_supplier_service;

ALTER TABLE tripsupport.suppliers
    ADD CONSTRAINT ck_supplier_service CHECK (service_type IN (
        'HANDLING', 'FUEL', 'CATERING', 'CREW_TRANSPORT', 'PAX_TRANSPORT',
        'CUSTOMS', 'DEICING', 'GAR', 'APIS',
        'PARKING', 'PPR', 'AIRPORT_SLOT', 'LANDING_PERMIT', 'FCP'));
