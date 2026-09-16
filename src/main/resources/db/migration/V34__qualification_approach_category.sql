-- ============================================================
--  V34 — la categorie d'approche par faible visibilite.
--
--  POURQUOI. Settings -> OPS Qualifications, dans l'annexe A4
--  (renderOpsQualSettings, l. 21018-21042), donne a chaque pilote une
--  categorie de CAT I a CAT IIIC, « affichee a cote de son nom dans
--  toute la plateforme » et censee alimenter la determination des
--  minimums. Rien ici ne la portait.
--
--  OU ELLE SE RANGE. Sur la qualification LVO, pas sur la personne.
--  Une categorie au-dessus de CAT I n'est pas un attribut : c'est une
--  formation, elle a une date de debut, une date de fin et une
--  reference, et crew.qualifications porte deja ces trois colonnes.
--  La mettre sur crew.persons aurait donne une categorie qui ne
--  perime jamais — exactement ce que fait le prototype, dont le menu
--  deroulant n'a ni date ni piece justificative.
--
--  CAT I EST L'ABSENCE DE LIGNE. Le prototype le dit lui-meme :
--  « Company standard: all flight crew are CAT I ». Un pilote sans
--  qualification LVO est donc CAT I, et on ne seme pas vingt lignes
--  qui affirmeraient une formation que personne n'a suivie. La ligne
--  LVO nait le jour ou quelqu'un monte un pilote au-dessus de CAT I.
-- ============================================================

ALTER TABLE crew.qualifications
    ADD COLUMN approach_category text;

ALTER TABLE crew.qualifications
    ADD CONSTRAINT ck_qualification_approach_category CHECK (
        approach_category IS NULL OR approach_category IN (
            'CAT_I', 'CAT_II', 'CAT_IIIA', 'CAT_IIIB', 'CAT_IIIC'));

-- Une categorie d'approche sur une qualification de marchandises
-- dangereuses ne voudrait rien dire : seule la ligne LVO la porte.
ALTER TABLE crew.qualifications
    ADD CONSTRAINT ck_qualification_approach_on_lvo CHECK (
        approach_category IS NULL OR kind = 'LVO');

COMMENT ON COLUMN crew.qualifications.approach_category IS
    'CAT_I a CAT_IIIC sur la ligne LVO. Pas de ligne LVO = CAT I, le standard compagnie.';
