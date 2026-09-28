-- Le dossier d'un membre d'equipage tel que Crew Management v226.211 le montre :
-- l'anciennete (« #85 » dans la table), la date d'embauche (tuile « New hires (90d) »)
-- et les coordonnees de la fiche (telephone, e-mail, contact d'urgence).
--
-- Colonnes facultatives, sans valeur par defaut : un dossier existant n'en a pas, et
-- l'ecran l'ecrit « — » plutot que d'afficher une valeur inventee.
ALTER TABLE crew.persons
    ADD COLUMN seniority_rank    integer,
    ADD COLUMN hire_date         date,
    ADD COLUMN phone             varchar(40),
    ADD COLUMN email             varchar(120),
    ADD COLUMN emergency_contact varchar(160);

ALTER TABLE crew.persons
    ADD CONSTRAINT persons_seniority_rank_positive CHECK (seniority_rank IS NULL OR seniority_rank > 0);
