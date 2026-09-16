-- ============================================================
--  Mettre en vol les etapes dont l'heure est passee
--
--  Pourquoi ce script existe
--  -------------------------
--  Flight Watch ne dessine que les appareils dont une POSITION est
--  connue. C'est la regle du module, et elle est juste : une etape
--  encore PLANNED n'a pas decolle, et la dessiner en vol serait
--  affirmer un fait qui n'existe pas.
--
--  Le prototype audite, lui, dessine ses trente-deux vols en l'air
--  en permanence — son propre pied de page l'admet : « flight data
--  simulated ». D'ou la carte pleine d'avions verts.
--
--  Sur des donnees semees, la journee ne s'anime donc jamais : les
--  etapes gardent le statut PLANNED que la migration leur a donne.
--  Ce script fait ce que ferait l'exploitation : il fait partir les
--  etapes dont l'heure de depart est passee, et pose une position
--  la ou l'appareil devrait etre.
--
--  Ce qui garde l'ecran honnete
--  ----------------------------
--    * provider = MANUAL, jamais ADSB. crew/ops distingue une
--      position RECUE d'une position SAISIE, et Flight Watch le
--      dit. Une position semee ne se fera jamais passer pour un
--      signal d'un recepteur ;
--    * source_type = 'seed', comme les 24 positions deja en base,
--      donc db/tools/shift_demo_day.sql les decale avec le reste ;
--    * la position est INTERPOLEE entre les deux terrains au
--      prorata du temps ecoule. Ce n'est pas une trajectoire : pas
--      de route, pas de vent, pas de niveau. C'est ou l'appareil
--      serait s'il volait en ligne droite a vitesse constante, et
--      c'est tout ce que ce script pretend.
--
--  Usage
--  -----
--    cd THENETWORKPLANBACK/networkplan
--    psql -U postgres -d networkplan -f db/tools/airborne_now.sql
--
--  Rejouable : relance sans effet sur une etape deja partie, et
--  remet a jour la position de celles qui volent encore.
--
--  Ce n'est PAS une migration Flyway : c'est un outil de
--  demonstration, jamais a lancer sur des donnees reelles.
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- Les etapes du jour dont la fenetre contient l'instant present.
CREATE TEMPORARY TABLE airborne ON COMMIT DROP AS
SELECT l.id,
       l.aircraft_id,
       l.dep_icao,
       l.arr_icao,
       l.std,
       l.sta,
       -- Part du trajet accomplie, bornee a [0,1].
       GREATEST(0, LEAST(1,
           EXTRACT(epoch FROM (now() - l.std))
           / NULLIF(EXTRACT(epoch FROM (l.sta - l.std)), 0))) AS flown
FROM ops.legs l
WHERE l.source_type = 'seed'
  AND l.status IN ('PLANNED', 'PREPARED', 'RELEASED', 'DEPARTED')
  AND l.std <= now()
  AND l.sta >= now();

-- Elles sont parties : l'heure de depart reelle est l'heure prevue,
-- faute de mieux, et le statut suit.
UPDATE ops.legs l SET
    status = 'DEPARTED',
    out_at = COALESCE(l.out_at, l.std),
    off_at = COALESCE(l.off_at, l.std + interval '8 minutes')
FROM airborne a
WHERE l.id = a.id;

-- La position, entre les deux terrains, au prorata du temps ecoule.
INSERT INTO ops.position_reports
    (id, tenant_id, aircraft_id, leg_id, provider, reported_at, received_at,
     latitude, longitude, altitude_ft, ground_speed_kt, track_deg, on_ground,
     source_type, source_ref)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000001',
       a.aircraft_id,
       a.id,
       'MANUAL',
       now(),
       now(),
       ROUND((dep.latitude + (arr.latitude - dep.latitude) * a.flown)::numeric, 4),
       ROUND((dep.longitude + (arr.longitude - dep.longitude) * a.flown)::numeric, 4),
       -- Un niveau de croisiere plausible, plat : ce script ne simule pas une
       -- montee ni une descente, et ne pretend pas le faire.
       39000,
       450,
       -- Le cap, du terrain de depart vers celui d'arrivee.
       MOD(CAST(DEGREES(ATAN2(
             SIN(RADIANS(arr.longitude - dep.longitude)) * COS(RADIANS(arr.latitude)),
             COS(RADIANS(dep.latitude)) * SIN(RADIANS(arr.latitude))
             - SIN(RADIANS(dep.latitude)) * COS(RADIANS(arr.latitude))
               * COS(RADIANS(arr.longitude - dep.longitude)))) AS int) + 360, 360),
       false,
       'seed',
       'airborne_now.sql'
FROM airborne a
JOIN refdata.airports dep ON dep.icao = a.dep_icao
JOIN refdata.airports arr ON arr.icao = a.arr_icao;

COMMIT;

\echo ''
\echo 'Etapes mises en vol et positions posees.'
\echo 'Verifier : SELECT status, count(*) FROM ops.legs'
\echo '           WHERE (std AT TIME ZONE ''UTC'')::date = CURRENT_DATE GROUP BY 1;'
