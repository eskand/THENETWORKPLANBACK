-- ============================================================
--  V47 — les chapitres ATA.
--
--  POURQUOI. Le Tech Log et la Hold Item List de l'annexe A4
--  affichent tous les deux, a cote du numero, le nom du systeme :
--  « ATA 32 — Landing Gear », « ATA 49 — APU ». Le prototype le
--  stocke sur chaque ligne de panne, ce qui veut dire que le meme
--  chapitre peut s'appeler autrement d'une ligne a l'autre.
--
--  Ici c'est une table de reference. Le chapitre est saisi ; le nom
--  est lu. Une panne mal rangee se voit alors — elle affiche le nom
--  du chapitre qu'on a reellement saisi, pas celui qu'on croyait.
--
--  LA SOURCE. ATA 100 / ATA iSpec 2200, chapitres 05 a 80. C'est la
--  numerotation que portent les manuels de maintenance, les MEL et
--  les comptes rendus d'evenement de toute l'industrie.
-- ============================================================

CREATE TABLE refdata.ata_chapters (
    chapter    text PRIMARY KEY,
    name       text NOT NULL,
    -- Le groupe de l'iSpec 2200 : cellule, systemes, motorisation.
    -- Il sert a regrouper une liste de pannes par grande famille.
    ata_group  text NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_ata_chapter CHECK (chapter ~ '^[0-9]{2}$')
);

INSERT INTO refdata.ata_chapters (chapter, name, ata_group, sort_order) VALUES
  ('05', 'Time Limits / Maintenance Checks', 'General',   5),
  ('06', 'Dimensions and Areas',             'General',   6),
  ('07', 'Lifting and Shoring',              'General',   7),
  ('08', 'Levelling and Weighing',           'General',   8),
  ('09', 'Towing and Taxiing',               'General',   9),
  ('10', 'Parking, Mooring, Storage',        'General',  10),
  ('11', 'Placards and Markings',            'General',  11),
  ('12', 'Servicing',                        'General',  12),
  ('14', 'Hardware',                         'General',  14),
  ('18', 'Vibration and Noise Analysis',     'General',  18),

  ('20', 'Standard Practices — Airframe',    'Systems',  20),
  ('21', 'Air Conditioning',                 'Systems',  21),
  ('22', 'Auto Flight',                      'Systems',  22),
  ('23', 'Communications',                   'Systems',  23),
  ('24', 'Electrical Power',                 'Systems',  24),
  ('25', 'Equipment / Furnishings',          'Systems',  25),
  ('26', 'Fire Protection',                  'Systems',  26),
  ('27', 'Flight Controls',                  'Systems',  27),
  ('28', 'Fuel',                             'Systems',  28),
  ('29', 'Hydraulic Power',                  'Systems',  29),
  ('30', 'Ice and Rain Protection',          'Systems',  30),
  ('31', 'Indicating / Recording Systems',   'Systems',  31),
  ('32', 'Landing Gear',                     'Systems',  32),
  ('33', 'Lights',                           'Systems',  33),
  ('34', 'Navigation',                       'Systems',  34),
  ('35', 'Oxygen',                           'Systems',  35),
  ('36', 'Pneumatic',                        'Systems',  36),
  ('37', 'Vacuum',                           'Systems',  37),
  ('38', 'Water / Waste',                    'Systems',  38),
  ('45', 'Central Maintenance System',       'Systems',  45),
  ('46', 'Information Systems',              'Systems',  46),
  ('47', 'Inert Gas System',                 'Systems',  47),
  ('49', 'Airborne Auxiliary Power (APU)',   'Systems',  49),

  ('51', 'Standard Practices — Structures',  'Structures', 51),
  ('52', 'Doors',                            'Structures', 52),
  ('53', 'Fuselage',                         'Structures', 53),
  ('54', 'Nacelles / Pylons',                'Structures', 54),
  ('55', 'Stabilizers',                      'Structures', 55),
  ('56', 'Windows',                          'Structures', 56),
  ('57', 'Wings',                            'Structures', 57),

  ('61', 'Propellers',                       'Propulsion', 61),
  ('70', 'Standard Practices — Engine',      'Propulsion', 70),
  ('71', 'Power Plant',                      'Propulsion', 71),
  ('72', 'Engine',                           'Propulsion', 72),
  ('73', 'Engine Fuel and Control',          'Propulsion', 73),
  ('74', 'Ignition',                         'Propulsion', 74),
  ('75', 'Air (Engine Bleed)',               'Propulsion', 75),
  ('76', 'Engine Controls',                  'Propulsion', 76),
  ('77', 'Engine Indicating',                'Propulsion', 77),
  ('78', 'Exhaust',                          'Propulsion', 78),
  ('79', 'Oil',                              'Propulsion', 79),
  ('80', 'Starting',                         'Propulsion', 80);
