-- ============================================================
--  V59 — La note de vol du menu ⋮ du dossier de vol
--
--  L'annexe distingue deux textes libres sur une etape, et elle a
--  raison : `flight._note` est la note d'exploitation — ce que le
--  poste de nuit doit savoir avant le depart, saisie au clic droit
--  sur la barre du Gantt ou par le menu ⋮ — tandis que
--  `flight._tripRemarks` est la remarque du dossier documentaire,
--  ecrite pendant et apres le vol. Les melanger dans la meme
--  colonne ferait ecraser l'une par l'autre.
--
--  `ops.legs.remark` porte deja la seconde (onglet TRIP FOLDER).
--  La premiere arrive ici, avec l'horodatage que l'annexe affiche a
--  cote du titre (`flight._noteAt`) et l'auteur, qu'elle n'avait
--  pas : une note d'exploitation sans auteur n'est pas exploitable
--  en analyse d'evenement.
-- ============================================================
ALTER TABLE ops.legs
    ADD COLUMN flight_note     text,
    ADD COLUMN flight_note_at  timestamptz,
    ADD COLUMN flight_note_by  uuid;

COMMENT ON COLUMN ops.legs.flight_note IS
    'Operational note on the leg — the flight file menu, distinct from remark (trip folder)';
