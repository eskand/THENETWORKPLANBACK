-- ============================================================
--  OUTIL DE DEMONSTRATION — pas une migration.
--
--  Amene deux echeances d'equipage dans la fenetre critique, pour
--  que l'ecran de suivi des validites ait quelque chose a montrer :
--   - un document deja perime,
--   - un document qui expire dans les 24 heures.
--
--  Pourquoi c'est en base et pas dans l'ecran :
--  le prototype audite ecrivait « 1 expiring within 24h » en dur dans
--  le composant. Ici les deux lignes existent dans crew.persons ; le
--  compte est celui que /v1/crew/expiries renvoie, avec la meme regle
--  de calcul (CrewDocumentChecker.checkDate) que pour les 37 autres.
--  Supprimer ces deux echeances fait disparaitre les deux mentions.
--
--  Choix des personnes : deterministe (les deux plus petits staff_no
--  parmi les commandants de bord actifs), pour que rejouer le script
--  ne deplace pas la demonstration d'une personne a l'autre.
--
--  Idempotent, et ne touche que source_type = 'seed'.
--  A rejouer apres shift_demo_day.sql.
--
--  Lancement :
--    psql -h localhost -U postgres -d networkplan -f seed_currency_edges.sql
-- ============================================================

BEGIN;

WITH cible AS (
    SELECT id,
           row_number() OVER (ORDER BY staff_no) AS rang
    FROM crew.persons
    WHERE source_type = 'seed'
      AND active
      AND main_role = 'CAPTAIN'
    LIMIT 2
)
UPDATE crew.persons p
SET medical_expiry = CASE c.rang
                         -- Perime depuis avant-hier : l'equipage est
                         -- indisponible, pas « bientot a renouveler ».
                         WHEN 1 THEN current_date - 2
                         -- Expire demain : encore valable aujourd'hui,
                         -- plus demain. C'est le cas que la fenetre de
                         -- 24 h existe pour attraper.
                         ELSE current_date + 1
                     END,
    source_ref = 'currency edge (seed_currency_edges.sql)',
    updated_at = now()
FROM cible c
WHERE p.id = c.id;

COMMIT;

-- Controle : ce que /v1/crew/expiries comptera.
SELECT count(*) FILTER (WHERE d < current_date)                         AS perimes,
       count(*) FILTER (WHERE d >= current_date AND d <= current_date + 1) AS sous_24h,
       count(*) FILTER (WHERE d <= current_date + 90)                   AS sous_90j
FROM (
    SELECT unnest(ARRAY[licence_expiry, medical_expiry, training_expiry]) AS d
    FROM crew.persons
    WHERE active
) AS echeances
WHERE d IS NOT NULL;
