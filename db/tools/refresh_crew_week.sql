-- ============================================================
--  Reposer la semaine d'equipage du prototype sur la semaine
--  courante
--
--  Pourquoi ce script existe
--  -------------------------
--  V28 pose le motif hebdomadaire du prototype sur la semaine qui
--  contenait le jour ou Flyway a tourne. Une migration ne tourne
--  qu'une fois : quinze jours plus tard, Crew Scheduling et Roster
--  retombent sur une semaine vide, exactement comme avant V28.
--
--  Ce script rejoue le meme travail pour la semaine d'aujourd'hui.
--  Il ne duplique pas le motif : il execute le fichier de migration
--  lui-meme, pour qu'il n'existe qu'UNE definition du motif. Deux
--  copies finiraient par diverger, et la demonstration montrerait
--  une semaine que la migration ne sait pas reproduire.
--
--  Rejouable
--  ---------
--  Toutes les insertions sont en ON CONFLICT DO NOTHING et les
--  identifiants sont des md5 deterministes : relancer le script sur
--  une semaine deja posee ne cree rien et ne casse rien. Une
--  cellule qu'un planificateur a modifiee a la main N'EST PAS
--  ecrasee — elle entre en conflit sur (version, personne, jour,
--  code) et le script passe son chemin.
--
--  Ce qu'il ne touche pas
--  ----------------------
--  Rien n'est supprime. Les semaines deja posees restent en base,
--  avec leurs etapes et leurs sieges : c'est l'historique de la
--  demonstration, et l'effacer priverait les compteurs de vol des
--  28 derniers jours de leur matiere.
--
--  Usage
--  -----
--    cd THENETWORKPLANBACK/networkplan
--    psql -U postgres -d networkplan -f db/tools/refresh_crew_week.sql
--
--  Ce n'est PAS une migration Flyway : c'est un outil d'exploitation
--  de la demonstration, a lancer quand on veut, et jamais en
--  production sur des donnees reelles.
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- Le chemin est relatif au repertoire d'ou psql est lance : la
-- racine du module, comme les autres outils de db/tools.
\i src/main/resources/db/migration/V28__seed_crew_week_from_prototype.sql

COMMIT;

\echo ''
\echo 'Semaine du prototype reposee sur la semaine courante.'
\echo 'Verifier : SELECT duty_date, count(*) FROM crew.roster_entries'
\echo '           WHERE source_ref LIKE ''NetPlus RFP%'' GROUP BY 1 ORDER BY 1;'
