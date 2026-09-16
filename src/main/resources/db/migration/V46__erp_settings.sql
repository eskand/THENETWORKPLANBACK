-- ============================================================
--  V46 — les trois coordonnees de crise.
--
--  POURQUOI. Le modele de communiqué de l'annexe A4 porte deux
--  jetons que personne ne peut inventer sous pression : {PHONE}, le
--  numero ouvert aux familles, et {MEDIA}, l'adresse presse. Le
--  troisieme, le lieu du centre de crise, est ce qu'on dit aux gens
--  qu'on convoque.
--
--  Les laisser dans le code aurait voulu dire qu'un changement de
--  numero passe par un deploiement. Un numero de crise qui sonne
--  dans le vide est pire que pas de numero du tout.
-- ============================================================

INSERT INTO platform.settings
    (tenant_id, category, setting_key, setting_value, default_value,
     value_type, unit, label, description, read_by, editable,
     section, group_title, control, placeholder, sort_order)
VALUES
  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'erp.crisisPhone',
   '+216 71 000 911', '+216 71 000 911',
   'STRING', NULL, 'Family enquiry line',
   'The number published in the first holding statement. It fills {PHONE} in every template.',
   'ErpConsoleService', true, 'SAFETY', 'Emergency response', 'TEXT', '+216 ...', 210),

  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'erp.mediaEmail',
   'press@networkplan.tn', 'press@networkplan.tn',
   'STRING', NULL, 'Media enquiries address',
   'Where journalists are sent. It fills {MEDIA} in every template.',
   'ErpConsoleService', true, 'SAFETY', 'Emergency response', 'TEXT', 'press@...', 220),

  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'erp.ercLocation',
   'Emergency Response Centre — OCC building, level 2, DTTA',
   'Emergency Response Centre — OCC building, level 2, DTTA',
   'STRING', NULL, 'Emergency Response Centre',
   'Where the crisis organisation assembles. Told to everyone who is called in.',
   'ErpConsoleService', true, 'SAFETY', 'Emergency response', 'TEXT', NULL, 230),

  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'erp.operatorName',
   'The Network Plan Airlines', 'The Network Plan Airlines',
   'STRING', NULL, 'Operator name for statements',
   'The name the operator issues statements under. It fills the header of every template.',
   'ErpConsoleService', true, 'SAFETY', 'Emergency response', 'TEXT', NULL, 240)
ON CONFLICT (tenant_id, setting_key) DO NOTHING;
