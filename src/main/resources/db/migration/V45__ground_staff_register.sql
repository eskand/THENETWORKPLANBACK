-- ============================================================
--  V45 — le registre du personnel au sol.
--
--  POURQUOI. La console de crise affiche une liste de disponibilite :
--  douze cellules, et en face de chacune un NOM. Pas un intitule de
--  poste — on n'appelle pas « Post Holder — Ground Operations » a
--  trois heures du matin, on appelle Belhadj S.
--
--  crew.persons ne porte que le personnel navigant : quarante pilotes
--  et dix PNC. Les dirigeants responsables, les post holders, les
--  regulateurs, l'OCC, la maintenance et l'escale n'y sont pas, et le
--  prototype les garde dans son propre registre SMS.
--
--  LE MEME REGISTRE SERT TROIS ECRANS. La disponibilite ERP y lit le
--  responsable de chaque cellule ; l'onglet Personnel du Safety
--  Manager y lit la formation SMS avec un denominateur reel ; le
--  formulaire de rapport y lit le declarant. Un registre, trois
--  lectures — trois listes auraient fini par diverger.
-- ============================================================

CREATE TABLE safety.ground_staff (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    full_name   text    NOT NULL,
    -- L'intitule exact du poste. La console de crise resout la
    -- cellule par cette chaine : la changer ici laisse une cellule
    -- sans responsable, et l'ecran le montre en rouge plutot que de
    -- se taire.
    role_title  text    NOT NULL,
    staff_group text    NOT NULL,
    base_icao   text,
    active      boolean NOT NULL DEFAULT true,
    sort_order  integer NOT NULL DEFAULT 0,

    CONSTRAINT uq_ground_staff UNIQUE (tenant_id, full_name, role_title)
);

CREATE INDEX ix_ground_staff_role ON safety.ground_staff (tenant_id, role_title);

INSERT INTO safety.ground_staff (tenant_id, source_type, source_ref, full_name, role_title, staff_group, base_icao, sort_order) VALUES
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Gharbi H.', 'Accountable Manager', 'Management', 'TUN', 1),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Dupont A.', 'Safety Manager', 'Management', 'TUN', 2),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Martin J.', 'Post Holder — Flight Operations / OCC Manager', 'Management', 'TUN', 3),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Haddad J.', 'Post Holder — Continuing Airworthiness (CAMO)', 'Management', 'TUN', 4),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Zaidi H.', 'Post Holder — Crew Training', 'Management', 'TUN', 5),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Belhadj S.', 'Post Holder — Ground Operations', 'Management', 'TUN', 6),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Roux T.', 'Senior Flight Dispatcher', 'Flight dispatch', 'TUN', 7),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Berriri S.', 'Flight Dispatcher', 'Flight dispatch', 'TUN', 8),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Chaabane M.', 'Flight Dispatcher', 'Flight dispatch', 'TUN', 9),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Ouali I.', 'Flight Dispatcher', 'Flight dispatch', 'TUN', 10),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Jelassi N.', 'Flight Dispatcher', 'Flight dispatch', 'TUN', 11),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Karray A.', 'Flight Dispatcher (trainee)', 'Flight dispatch', 'TUN', 12),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Mabrouk R.', 'OCC Duty Manager', 'OCC', 'TUN', 13),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Sassi W.', 'OCC Duty Officer', 'OCC', 'TUN', 14),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Hamdi L.', 'OCC Duty Officer', 'OCC', 'TUN', 15),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Tounsi K.', 'Flight Watch Officer', 'OCC', 'TUN', 16),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Bouaziz F.', 'Line Maintenance Engineer', 'Maintenance / CAMO', 'TUN', 17),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Nasri M.', 'Line Maintenance Engineer', 'Maintenance / CAMO', 'TUN', 18),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Amri T.', 'Certifying Staff (B1)', 'Maintenance / CAMO', 'TUN', 19),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Slimani H.', 'Certifying Staff (B2 avionics)', 'Maintenance / CAMO', 'TUN', 20),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Rekik Y.', 'CAMO Airworthiness Reviewer', 'Maintenance / CAMO', 'TUN', 21),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Jaziri A.', 'Ramp Supervisor', 'Ground operations', 'TUN', 22),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Mejri S.', 'Ramp Agent', 'Ground operations', 'TUN', 23),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Chakroun O.', 'Ramp Agent', 'Ground operations', 'TUN', 24),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Fakhfakh B.', 'Refuelling Coordinator', 'Ground operations', 'TUN', 25),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Dridi M.', 'Load Control Officer', 'Ground operations', 'TUN', 26),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Halouani R.', 'Passenger Services Agent', 'Ground operations', 'TUN', 27),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Ayadi N.', 'De-icing Technician', 'Ground operations', 'TUN', 28),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Zouari S.', 'Ground Equipment Technician', 'Ground operations', 'TUN', 29),
  ('00000000-0000-0000-0000-000000000001', 'seed', 'annexe A4 - SMS.seedGroundOnly', 'Trigui F.', 'Cargo & Dangerous Goods Agent', 'Ground operations', 'TUN', 30);
