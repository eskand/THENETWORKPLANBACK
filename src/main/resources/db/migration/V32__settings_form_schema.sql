-- ============================================================
--  V32 — les reglages deviennent un formulaire, pas une liste.
--
--  POURQUOI. L'ecran Settings de l'annexe A4 (SETTINGS_SCHEMA,
--  l. 66172-66338) n'est pas un tableau de cles : c'est onze
--  rubriques, chacune decoupee en groupes, chaque ligne portant un
--  libelle, une explication et UN CONTROLE choisi selon ce que la
--  valeur veut dire — un interrupteur pour un oui/non, un segment
--  pour un choix a deux, un selecteur pour un choix a six, un
--  nuancier pour une couleur.
--
--  Jusqu'ici la table ne savait dire qu'une chose : « une chaine ».
--  Le seuil de retard, le fuseau de reference et la couleur d'un vol
--  AOG s'y saisissaient tous les trois dans le meme champ texte.
--
--  CE QUI EST AJOUTE. De quoi dessiner le formulaire depuis la base
--  et non depuis le code du navigateur : la rubrique, le groupe, le
--  type de controle, les options d'un choix, les bornes d'un nombre,
--  l'ordre d'affichage, et la valeur par defaut.
--
--  POURQUOI EN BASE ET PAS DANS LE JSX. Parce que le schema est une
--  donnee d'exploitation : deux exploitants n'offrent pas les memes
--  options de fuseau ni les memes services par defaut. Le prototype
--  n'avait pas le choix — il n'a pas de serveur, et range tout dans
--  localStorage, donc chaque navigateur a sa propre configuration.
--
--  read_by DEVIENT NULLABLE. C'est le point honnete de cette
--  migration. La colonne dit quel service lit la cle ; elle etait
--  obligatoire parce que l'audit avait trouve, dans le prototype,
--  des reglages que personne ne lisait — « un seuil que personne
--  n'applique est pire que pas de seuil, parce qu'il a l'air d'etre
--  un controle ». En reprenant les 54 champs du prototype on importe
--  ce probleme. Plutot que d'inventer un lecteur pour chacun, NULL
--  dit la verite : la ligne existe, elle se saisit, et rien ne la lit
--  encore. L'ecran l'affiche, et la liste des cles a brancher se lit
--  en une requete.
-- ============================================================

ALTER TABLE platform.settings
    -- La rubrique de gauche : une des onze du prototype, plus celles
    -- qu'imposent les reglages deja vivants de cette application.
    ADD COLUMN section       text,
    -- Le titre du bloc dans la rubrique : « Operations centre », « Units ».
    ADD COLUMN group_title   text,
    -- Le controle a dessiner. Distinct de value_type : un oui/non est
    -- toujours un BOOLEAN, mais il se dessine en interrupteur ici et
    -- pourrait se dessiner en case a cocher ailleurs.
    ADD COLUMN control       text,
    -- [{"value":"UTC","label":"UTC (Zulu)"}, ...] pour SELECT et SEGMENT.
    ADD COLUMN options       jsonb,
    ADD COLUMN placeholder   text,
    ADD COLUMN min_value     numeric,
    ADD COLUMN max_value     numeric,
    ADD COLUMN step_value    numeric,
    ADD COLUMN sort_order    integer NOT NULL DEFAULT 0,
    -- Ce a quoi « Reinitialiser cette rubrique » ramene. Sans cette
    -- colonne le bouton du prototype n'a rien a restaurer : lui relit
    -- les defauts dans son propre code source.
    ADD COLUMN default_value text,
    -- Une ligne qui ne s'affiche que si une autre est active :
    -- l'intervalle de rafraichissement n'a de sens que rafraichissement
    -- automatique allume.
    ADD COLUMN show_if_key   text,
    ADD COLUMN show_if_value text;

-- LIST : une valeur qui est une suite d'elements, stockee en CSV comme
-- tripsupport.expected-services l'etait deja. Relache AVANT les UPDATE qui
-- s'en servent, sinon la contrainte refuse la ligne qu'on est en train de
-- qualifier.
ALTER TABLE platform.settings DROP CONSTRAINT ck_setting_type;
ALTER TABLE platform.settings ADD CONSTRAINT ck_setting_type CHECK (value_type IN (
    'STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DURATION', 'ENUM', 'LIST'));

-- Les reglages existants gardent leur valeur comme defaut : c'est celle
-- que la migration de seed leur avait donnee, et aucune n'a ete changee
-- par un exploitant a ce stade.
UPDATE platform.settings SET default_value = setting_value WHERE default_value IS NULL;

-- Les quatorze lignes deja en place prennent leur rubrique et leur
-- controle. Aucune ne change de cle : les services qui les lisent ne
-- voient rien bouger.
UPDATE platform.settings SET section = 'SALES', group_title = 'Pipeline', control = 'SELECT', sort_order = 10,
       options = '[{"value":"EUR","label":"EUR — euro"},{"value":"USD","label":"USD — US dollar"},{"value":"TND","label":"TND — Tunisian dinar"},{"value":"GBP","label":"GBP — pound sterling"}]'
 WHERE setting_key = 'sales.pipeline-currency';

UPDATE platform.settings SET section = 'CREW', group_title = 'Documents', control = 'NUMBER',
       min_value = 1, max_value = 365, step_value = 1, sort_order = 30
 WHERE setting_key = 'crew.document-warning-window';
UPDATE platform.settings SET section = 'CREW', group_title = 'Rest & planning', control = 'NUMBER',
       min_value = 480, max_value = 1440, step_value = 30, sort_order = 10
 WHERE setting_key = 'crew.minimum-rest';
UPDATE platform.settings SET section = 'CREW', group_title = 'Rest & planning', control = 'NUMBER',
       min_value = 15, max_value = 240, step_value = 5, sort_order = 20
 WHERE setting_key = 'crew.report-before-std';

UPDATE platform.settings SET section = 'MAINTENANCE', group_title = 'Due list', control = 'NUMBER',
       min_value = 1, max_value = 180, step_value = 1, sort_order = 10
 WHERE setting_key = 'camo.due-warning-days';
UPDATE platform.settings SET section = 'MAINTENANCE', group_title = 'Due list', control = 'NUMBER',
       min_value = 1, max_value = 500, step_value = 5, sort_order = 20
 WHERE setting_key = 'camo.due-warning-hours';

UPDATE platform.settings SET section = 'ALERTS', group_title = 'Operational thresholds', control = 'NUMBER',
       min_value = 0, max_value = 240, step_value = 5, sort_order = 10
 WHERE setting_key = 'ops.delay-threshold';
UPDATE platform.settings SET section = 'TIMELINE', group_title = 'Rotations', control = 'NUMBER',
       min_value = 10, max_value = 240, step_value = 5, sort_order = 10
 WHERE setting_key = 'ops.minimum-turnaround';
UPDATE platform.settings SET section = 'DISPATCH', group_title = 'Performance', control = 'NUMBER',
       min_value = 50, max_value = 100, step_value = 1, sort_order = 10
 WHERE setting_key = 'ops.otp-target';
UPDATE platform.settings SET section = 'FOLLOWING', group_title = 'Deviation detection', control = 'NUMBER',
       min_value = 1, max_value = 60, step_value = 1, sort_order = 30
 WHERE setting_key = 'ops.position-stale-after';
UPDATE platform.settings SET section = 'DISPATCH', group_title = 'Flight package', control = 'NUMBER',
       min_value = 5, max_value = 360, step_value = 5, sort_order = 40
 WHERE setting_key = 'ops.weather-stale-after';

UPDATE platform.settings SET section = 'DATA', group_title = 'Performance', control = 'NUMBER',
       min_value = 0, max_value = 600, step_value = 5, sort_order = 20
 WHERE setting_key = 'platform.dispatch-board-cache';

UPDATE platform.settings SET section = 'SAFETY', group_title = 'Risk matrix', control = 'TEXT', sort_order = 20
 WHERE setting_key = 'safety.matrix-revision';

UPDATE platform.settings SET section = 'DISPATCH', group_title = 'Services offered by default', control = 'TAGS',
       value_type = 'LIST', placeholder = 'Add a service…', sort_order = 10
 WHERE setting_key = 'tripsupport.expected-services';

-- Le formulaire ne peut pas dessiner une ligne sans rubrique, sans groupe,
-- sans controle ni sans defaut : la contrainte le garantit maintenant que
-- l'existant est renseigne.
ALTER TABLE platform.settings
    ALTER COLUMN section       SET NOT NULL,
    ALTER COLUMN group_title   SET NOT NULL,
    ALTER COLUMN control       SET NOT NULL,
    ALTER COLUMN default_value SET NOT NULL;

-- NULL = rien ne lit encore cette cle. Voir l'en-tete.
ALTER TABLE platform.settings ALTER COLUMN read_by DROP NOT NULL;

ALTER TABLE platform.settings
    ADD CONSTRAINT ck_setting_section CHECK (section IN (
        'GENERAL', 'ALERTS', 'TIMELINE', 'DISPATCH', 'FOLLOWING', 'CREW',
        'SAFETY', 'AIRPORTS', 'DATA', 'OPSQUAL', 'ADMINISTRATION',
        -- Deux rubriques de plus que le prototype, parce que deux reglages
        -- deja lus par un service n'entraient dans aucune des siennes.
        'MAINTENANCE', 'SALES')),
    ADD CONSTRAINT ck_setting_control CHECK (control IN (
        'TEXT', 'NUMBER', 'SELECT', 'SEGMENT', 'TOGGLE', 'COLOR', 'TAGS', 'TIME')),
    -- Un choix sans options serait un menu vide.
    ADD CONSTRAINT ck_setting_options CHECK (
        control NOT IN ('SELECT', 'SEGMENT') OR options IS NOT NULL),
    -- Une condition d'affichage a besoin des deux moities.
    ADD CONSTRAINT ck_setting_show_if CHECK (
        (show_if_key IS NULL) = (show_if_value IS NULL));

CREATE INDEX ix_settings_form ON platform.settings (tenant_id, section, sort_order);
