-- ============================================================
--  V42 — la communication de securite.
--
--  POURQUOI. Safety Promotion, dans l'annexe A4, est un FIL de
--  communications publiees par le responsable securite et lues par
--  tout le personnel : une alerte, un bulletin, un retour
--  d'experience, une politique. Chacune porte un type, une date, un
--  auteur nomme, un public vise, et un compteur de lecture.
--
--  safety.campaigns portait presque tout cela — titre, message,
--  public, accuse de lecture — mais lui manquaient les trois choses
--  qui font qu'une communication est une communication et pas une
--  campagne :
--
--    kind         — une ALERTE ne se lit pas comme une POLITIQUE.
--                   L'une demande une action avant le prochain vol,
--                   l'autre decrit une regle permanente. Les ranger
--                   ensemble sous « campagne » les rend equivalentes
--                   a l'ecran, ce qu'elles ne sont pas.
--    author_name  — une communication de securite est signee. Une
--                   consigne non signee n'engage personne, et le
--                   lecteur a le droit de savoir qui la porte.
--    published_on — la date de PARUTION, distincte de starts_on.
--                   Une campagne court sur une periode ; une alerte
--                   parait un jour et reste vraie ensuite.
--
--  CE QUI EST RE-SEME. Les quatre campagnes de demonstration sont
--  remplacees par les quatre communications du prototype. Ce sont
--  des donnees de demonstration des deux cotes ; garder les deux
--  jeux aurait donne huit entrees la ou l'ecran de reference en
--  montre quatre.
--
--  CE QUI NE BOUGE PAS. safety.campaign_acknowledgements et son
--  unicite par (campagne, personne) : le mecanisme « j'ai lu ceci »
--  etait deja juste, et c'est lui qui alimente le « 9 / 24 read ».
-- ============================================================

ALTER TABLE safety.campaigns
    ADD COLUMN kind         text,
    ADD COLUMN author_name  text,
    ADD COLUMN published_on date;

-- L'existant prend une valeur avant que la contrainte ne s'applique.
UPDATE safety.campaigns
   SET kind = 'BULLETIN',
       published_on = starts_on,
       author_name = COALESCE(author_name, 'Safety Manager')
 WHERE kind IS NULL;

ALTER TABLE safety.campaigns
    ALTER COLUMN kind SET NOT NULL,
    ALTER COLUMN published_on SET NOT NULL,
    ADD CONSTRAINT ck_campaign_kind CHECK (kind IN ('ALERT', 'BULLETIN', 'LESSON', 'POLICY'));

COMMENT ON COLUMN safety.campaigns.kind IS
    'ALERT agit avant le prochain vol · BULLETIN informe · LESSON tire une lecon '
    'd''un evenement · POLICY enonce une regle permanente.';

-- ------------------------------------------------------------
--  Les quatre communications du prototype
-- ------------------------------------------------------------
DELETE FROM safety.campaign_acknowledgements
 WHERE campaign_id IN (SELECT id FROM safety.campaigns WHERE reference LIKE 'CMP-%');
DELETE FROM safety.campaigns WHERE reference LIKE 'CMP-%';

INSERT INTO safety.campaigns
    (id, tenant_id, source_type, source_ref, reference, kind, title, theme, message,
     author_name, published_on, starts_on, ends_on, audience, status, acknowledgement_required)
VALUES
  (md5('sb:SB-2026-0004')::uuid, '00000000-0000-0000-0000-000000000001', 'seed',
   'NetPlus RFP annexe A4 — TNPSMS bulletins',
   'SB-2026-0004', 'ALERT',
   'Safety alert — wildlife activity at LFPG during early morning operations',
   'RUNWAY_SAFETY',
   'Following a bird strike on final approach to RWY 27R, crews operating into LFPG between 05:00 '
   'and 08:00 UTC are to request current wildlife activity information from ATC before commencing '
   'the approach and to report all strikes, however minor.',
   'Dupont A. — Safety Manager', DATE '2026-07-27', DATE '2026-07-27', NULL,
   'FLIGHT_CREW', 'RUNNING', true),

  (md5('sb:SB-2026-0003')::uuid, '00000000-0000-0000-0000-000000000001', 'seed',
   'NetPlus RFP annexe A4 — TNPSMS bulletins',
   'SB-2026-0003', 'POLICY',
   'Just Culture — how a confidential report is handled',
   'JUST_CULTURE',
   'A confidential report is de-identified by the Safety Manager before any analysis is shared. '
   'Reporting an honest error will never of itself lead to disciplinary action. Wilful violations '
   'and destructive acts remain outside the protection of the policy.',
   'Gharbi H. — Accountable Manager', DATE '2026-07-10', DATE '2026-07-10', NULL,
   'ALL', 'RUNNING', true),

  (md5('sb:SB-2026-0002')::uuid, '00000000-0000-0000-0000-000000000001', 'seed',
   'NetPlus RFP annexe A4 — TNPSMS bulletins',
   'SB-2026-0002', 'LESSON',
   'Lesson learned — late NOTAM publication and the operational flight plan',
   'DISPATCH',
   'A temporary restricted area published after OFP generation was not seen before departure. '
   'Until the checklist revision is issued, dispatchers are to re-check NOTAMs within 60 minutes '
   'of the crew briefing for all European sectors.',
   'Martin J. — Post Holder Flight Operations / OCC Manager', DATE '2026-07-01', DATE '2026-07-01',
   NULL, 'OFFICE', 'RUNNING', true),

  (md5('sb:SB-2026-0001')::uuid, '00000000-0000-0000-0000-000000000001', 'seed',
   'NetPlus RFP annexe A4 — TNPSMS bulletins',
   'SB-2026-0001', 'BULLETIN',
   'Safety objectives 2026 and how they are measured',
   'OBJECTIVES',
   'The 2026 safety objectives, the safety performance indicators used to measure them and the '
   'alert levels that trigger a Safety Review Board are published in the SMS manual, section 3.4.',
   'Gharbi H. — Accountable Manager', DATE '2026-01-15', DATE '2026-01-15', NULL,
   'ALL', 'RUNNING', true)
ON CONFLICT (id) DO NOTHING;

CREATE INDEX ix_campaigns_published ON safety.campaigns (tenant_id, published_on DESC);
