-- ============================================================
--  OUTIL DE DEMONSTRATION — pas une migration.
--
--  Pose une journee d'exploitation la veille de la journee de
--  demonstration, pour que le tableau de bord ait quelque chose
--  a comparer.
--
--  Pourquoi un outil et pas un rendu :
--  le prototype audite affichait « ↑6 vs yesterday » sans qu'aucune
--  veille existe — le chiffre etait fabrique au moment du rendu.
--  Ici la veille est faite d'etapes reelles dans ops.legs ; le KPI
--  les compte avec exactement la meme requete que celles d'aujourd'hui
--  (DispatchBoardService lit findProgramme(date.minusDays(1))). Si on
--  supprime ces lignes, la puce disparait de l'ecran. C'est la difference
--  entre une donnee et une decoration.
--
--  Ce que la veille contient :
--   - les 26 premieres etapes de la journee courante, decalees de 24 h.
--     Une journee plus courte que celle d'aujourd'hui, ce qui est le cas
--     ordinaire : on ne recopie pas le programme a l'identique ;
--   - toutes parties et arrivees (statut CLOSED) : la veille est finie ;
--   - un retard reel sur celles dont l'empreinte le designe, de facon
--     deterministe — rejouer le script donne le meme resultat.
--
--  Idempotent : ON CONFLICT sur la cle metier. Ne touche que
--  source_type = 'seed'. A rejouer apres shift_demo_day.sql.
--
--  Lancement :
--    psql -h localhost -U postgres -d networkplan -f seed_previous_day.sql
-- ============================================================

BEGIN;

WITH today AS (
    SELECT max((std AT TIME ZONE 'UTC')::date) AS d
    FROM ops.legs
    WHERE source_type = 'seed'
),
programme AS (
    SELECT l.*,
           row_number() OVER (ORDER BY l.std, l.flight_no) AS rank,
           -- Empreinte stable de l'etape : sert a designer les retards
           -- sans tirage aleatoire, pour que deux executions donnent
           -- la meme veille.
           ('x' || substr(md5(l.business_key), 1, 8))::bit(32)::bigint AS fingerprint
    FROM ops.legs l, today t
    WHERE l.source_type = 'seed'
      AND (l.std AT TIME ZONE 'UTC')::date = t.d
      AND l.status <> 'CANCELLED'
),
kept AS (
    SELECT *,
           -- Un retard sur environ une etape sur six, cale entre 22 et
           -- 47 minutes : au-dela du seuil de 15 min de l'exploitant,
           -- donc compte comme retard par delayMinutes() et manque par
           -- onTimePerformance().
           CASE WHEN fingerprint % 6 = 0
                THEN 22 + (fingerprint % 26)
                ELSE fingerprint % 9        -- de 0 a 8 min : a l'heure
           END AS out_delay_min
    FROM programme
    WHERE rank <= 26
)
INSERT INTO ops.legs (id, tenant_id, source_type, source_ref, source_version, source_at,
                      trip_id, aircraft_id, flight_no, dep_icao, arr_icao, base_icao,
                      std, sta, etd, eta, out_at, off_at, on_at, in_at,
                      status, flight_type, pax_count, risk_level, remark, business_key)
SELECT md5('prev-' || k.business_key)::uuid,
       k.tenant_id,
       'seed',
       'previous operating day (seed_previous_day.sql)',
       k.source_version,
       now(),
       NULL,                                   -- pas de trip : le voyage d'hier est clos
       k.aircraft_id,
       k.flight_no,
       k.dep_icao, k.arr_icao, k.base_icao,
       k.std - interval '1 day',
       k.sta - interval '1 day',
       k.std - interval '1 day' + make_interval(mins => k.out_delay_min::int),
       k.sta - interval '1 day' + make_interval(mins => k.out_delay_min::int),
       k.std - interval '1 day' + make_interval(mins => k.out_delay_min::int),
       k.std - interval '1 day' + make_interval(mins => (k.out_delay_min + 8)::int),
       k.sta - interval '1 day' + make_interval(mins => (k.out_delay_min - 6)::int),
       k.sta - interval '1 day' + make_interval(mins => k.out_delay_min::int),
       'CLOSED',
       k.flight_type,
       k.pax_count,
       k.risk_level,
       NULL,
       k.business_key || '-D1'
FROM kept k
ON CONFLICT ON CONSTRAINT uq_legs_business_key DO NOTHING;

COMMIT;

-- Controle : ce que la veille pese reellement, et la ponctualite
-- que le serveur en tirera (depart dans les 15 min du programme).
SELECT (std AT TIME ZONE 'UTC')::date            AS jour,
       count(*)                                  AS etapes,
       count(*) FILTER (WHERE out_at IS NOT NULL) AS parties,
       count(*) FILTER (WHERE out_at > std + interval '15 minutes') AS en_retard,
       round(100.0 * count(*) FILTER (WHERE out_at <= std + interval '15 minutes')
             / nullif(count(*) FILTER (WHERE out_at IS NOT NULL), 0)) AS otp_pct
FROM ops.legs
WHERE source_type = 'seed'
GROUP BY 1
ORDER BY 1;
