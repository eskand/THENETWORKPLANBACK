-- ============================================================
--  V39 — qui repond du systeme de gestion de la securite.
--
--  POURQUOI. Le panneau « SMS accountability » de l'annexe A4 nomme
--  le dirigeant responsable, le responsable securite, l'exploitant et
--  la reference du CTA. Ce ne sont pas des libelles d'ecran : l'OACI
--  (annexe 19) et l'AESA (ORO.GEN.210) exigent que ces personnes
--  soient DESIGNEES, et un audit demande a les voir.
--
--  OU CA SE RANGE. Dans platform.settings, rubrique SAFETY, avec le
--  reste de la configuration — pas dans une table a part. Ce sont des
--  reglages d'exploitant : ils se saisissent, ils se relisent, ils
--  s'exportent avec la configuration, et le formulaire de V32 sait
--  deja les dessiner.
--
--  read_by. SafetyOverviewService les lit vraiment pour remplir le
--  panneau : ces quatre lignes ne font pas partie des reglages encore
--  debranches.
-- ============================================================

INSERT INTO platform.settings
    (tenant_id, category, setting_key, setting_value, default_value,
     value_type, unit, label, description, read_by, editable,
     section, group_title, control, placeholder, sort_order)
VALUES
  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'safety.accountableManager',
   'Gharbi H. — Accountable Manager', 'Gharbi H. — Accountable Manager',
   'STRING', NULL, 'Accountable Manager',
   'The person who answers for the safety management system. ICAO Annex 19 / EASA ORO.GEN.210.',
   'SafetyOverviewService', true, 'SAFETY', 'Accountability', 'TEXT', 'Name — role', 110),

  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'safety.safetyManager',
   'Dupont A. — Safety Manager', 'Dupont A. — Safety Manager',
   'STRING', NULL, 'Safety Manager',
   'The nominated person for safety. Shown on every published safety communication.',
   'SafetyOverviewService', true, 'SAFETY', 'Accountability', 'TEXT', 'Name — role', 120),

  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'safety.aocReference',
   'TN-AOC-2019-0187', 'TN-AOC-2019-0187',
   'STRING', NULL, 'AOC reference',
   'The air operator certificate the safety management system belongs to.',
   'SafetyOverviewService', true, 'SAFETY', 'Accountability', 'TEXT', 'XX-AOC-YYYY-NNNN', 130),

  ('00000000-0000-0000-0000-000000000001', 'SAFETY', 'safety.reviewCycleDays',
   '90', '90',
   'INTEGER', 'days', 'Safety review board cycle',
   'How often the safety review board meets, and therefore how long a risk may sit unreviewed.',
   NULL, true, 'SAFETY', 'Accountability', 'NUMBER', NULL, 140)
ON CONFLICT (tenant_id, setting_key) DO NOTHING;

UPDATE platform.settings SET min_value = 7, max_value = 365, step_value = 1
 WHERE setting_key = 'safety.reviewCycleDays';
