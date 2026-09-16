-- ============================================================
--  Ramener la journée de démonstration à aujourd'hui
--
--  Pourquoi ce script existe
--  -------------------------
--  Les seeds (V8, V10, V12, V14) construisent leurs heures avec
--  now() AU MOMENT DE LA MIGRATION. La journée d'exploitation est
--  donc figée au jour où Flyway a tourné. Le lendemain, l'OCC
--  demande « aujourd'hui », ne trouve aucune étape, et l'écrit —
--  ce qui est honnête mais inutilisable pour une démonstration.
--
--  Ce script décale toutes les lignes SEMÉES d'un nombre entier de
--  jours, de sorte que la journée retombe sur aujourd'hui. Il ne
--  touche à rien d'autre :
--    * seules les lignes portant source_type = 'seed' sont visées,
--      donc une étape saisie par un opérateur n'est jamais déplacée ;
--    * le décalage est un nombre entier de jours, donc les heures
--      UTC, les rotations et les temps au sol sont conservés ;
--    * business_key ne contient pas la date (« TNP-TS-NPE-TNP410 »),
--      il reste donc valide ;
--    * les lignes posees par V28 (etapes « PROTO-… », version de roster
--      « Week of … ») ne bougent PAS. Elles sont datees par rapport a la
--      semaine courante, leurs identifiants et leurs cles metier derivent de
--      la date, et refresh_crew_week.sql les repose quand il faut. Les
--      decaler casserait ces trois liens d'un coup.
--
--  Rejouable : si la journée est déjà aujourd'hui, le décalage vaut
--  zéro et le script ne modifie rien.
--
--  Usage
--  -----
--    psql -U postgres -d networkplan -f db/tools/shift_demo_day.sql
--
--  Ce n'est PAS une migration Flyway : c'est un outil d'exploitation
--  de la démonstration, à lancer quand on veut, et jamais en
--  production sur des données réelles.
-- ============================================================

\set ON_ERROR_STOP on

BEGIN;

-- D'abord, effacer la veille posée par seed_previous_day.sql.
--
-- Sans cela, le calcul du décalage ci-dessous prend le MIN des dates
-- semées et vise la veille au lieu de la journée principale : la
-- journée partait à J+1 au lieu d'aujourd'hui. Ces lignes sont de
-- toute façon régénérées en relançant seed_previous_day.sql, et rien
-- ne les référence (ni équipage, ni service, ni release).
DELETE FROM ops.legs
WHERE source_type = 'seed'
  AND source_ref = 'previous operating day (seed_previous_day.sql)';

-- Le décalage : de la journée semée vers aujourd'hui.
CREATE TEMPORARY TABLE demo_shift ON COMMIT DROP AS
SELECT COALESCE(
         (CURRENT_DATE - MIN((std AT TIME ZONE 'UTC')::date)),
         0
       ) AS days
FROM ops.legs
WHERE source_type = 'seed';

DO $$
DECLARE
    shift_days integer;
BEGIN
    SELECT days INTO shift_days FROM demo_shift;

    IF shift_days IS NULL OR shift_days = 0 THEN
        RAISE NOTICE 'La journee de demonstration est deja datee d aujourd hui : rien a faire.';
        RETURN;
    END IF;

    RAISE NOTICE 'Decalage de % jour(s).', shift_days;

    -- DOM1 — le programme
    UPDATE ops.legs SET
        std = std + (shift_days * interval '1 day'),
        sta = sta + (shift_days * interval '1 day'),
        etd = etd + (shift_days * interval '1 day'),
        eta = eta + (shift_days * interval '1 day'),
        out_at = out_at + (shift_days * interval '1 day'),
        off_at = off_at + (shift_days * interval '1 day'),
        on_at = on_at + (shift_days * interval '1 day'),
        in_at = in_at + (shift_days * interval '1 day'),
        ctot = ctot + (shift_days * interval '1 day'),
        mvt_sent_at = mvt_sent_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed'
      AND business_key NOT LIKE 'PROTO-%';

    UPDATE ops.position_reports SET
        reported_at = reported_at + (shift_days * interval '1 day'),
        received_at = received_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed';

    -- DOM4 — équipage : affectations, périodes de service, roster
    UPDATE crew.leg_assignments SET
        duty_start = duty_start + (shift_days * interval '1 day'),
        duty_end = duty_end + (shift_days * interval '1 day'),
        checked_in_at = checked_in_at + (shift_days * interval '1 day'),
        checked_out_at = checked_out_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed'
      AND leg_id NOT IN (SELECT id FROM ops.legs WHERE business_key LIKE 'PROTO-%');

    UPDATE crew.duty_periods SET
        report_at = report_at + (shift_days * interval '1 day'),
        off_duty_at = off_duty_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed'
      AND (leg_id IS NULL
           OR leg_id NOT IN (SELECT id FROM ops.legs WHERE business_key LIKE 'PROTO-%'));

    -- Le roster se décale en deux temps.
    --
    -- uq_roster_entry porte sur (version, personne, jour, code) et n'est pas
    -- différable : décaler d'un jour ferait, le temps de la mise à jour,
    -- exister deux lignes sur le même jour. On envoie donc d'abord toutes les
    -- dates dix ans plus loin, où aucune collision n'est possible, puis on
    -- revient à la cible. Deux écritures, aucun contournement de contrainte.
    UPDATE crew.roster_versions SET
        period_start = period_start + 3650,
        period_end = period_end + 3650,
        published_at = published_at + interval '3650 days'
    WHERE source_type = 'seed' AND source_ref NOT LIKE 'NetPlus RFP annexe A4%';
    UPDATE crew.roster_versions SET
        period_start = period_start - 3650 + shift_days,
        period_end = period_end - 3650 + shift_days,
        published_at = published_at - interval '3650 days' + (shift_days * interval '1 day')
    WHERE source_type = 'seed' AND source_ref NOT LIKE 'NetPlus RFP annexe A4%';

    UPDATE crew.roster_entries SET duty_date = duty_date + 3650 WHERE source_type = 'seed' AND source_ref NOT LIKE 'NetPlus RFP annexe A4%';
    UPDATE crew.roster_entries SET duty_date = duty_date - 3650 + shift_days WHERE source_type = 'seed' AND source_ref NOT LIKE 'NetPlus RFP annexe A4%';

    UPDATE crew.absences SET
        starts_on = starts_on + shift_days,
        ends_on = ends_on + shift_days
    WHERE source_type = 'seed';

    -- DOM5 — maintenance : utilisation, carnet, défauts, MEL
    UPDATE camo.utilisation SET
        flown_on = flown_on + shift_days
    WHERE source_type = 'seed';

    -- La référence de page porte la date (« TL-20260909-BAC63 ») : elle est
    -- reconstruite pour rester lisible, en gardant son suffixe, donc son
    -- unicité.
    UPDATE camo.tech_log_entries SET
        flown_on = flown_on + shift_days,
        signed_at = signed_at + (shift_days * interval '1 day'),
        page_ref = 'TL-' || to_char(flown_on + shift_days, 'YYYYMMDD')
                   || '-' || right(page_ref, 5)
    WHERE source_type = 'seed';

    UPDATE camo.defects SET
        reported_at = reported_at + (shift_days * interval '1 day'),
        closed_at = closed_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed';

    UPDATE camo.mel_items SET
        raised_at = raised_at + (shift_days * interval '1 day'),
        due_at = due_at + (shift_days * interval '1 day'),
        closed_at = closed_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed';

    UPDATE camo.aircraft SET
        status_since = status_since + (shift_days * interval '1 day')
    WHERE source_type = 'seed' AND status_since IS NOT NULL;

    -- DOM2 — trip support : demandes de service et de permis
    UPDATE tripsupport.service_requests SET
        sent_at = sent_at + (shift_days * interval '1 day'),
        acknowledged_at = acknowledged_at + (shift_days * interval '1 day'),
        confirmed_at = confirmed_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed';

    UPDATE tripsupport.permit_requests SET
        sent_at = sent_at + (shift_days * interval '1 day'),
        acknowledged_at = acknowledged_at + (shift_days * interval '1 day'),
        confirmed_at = confirmed_at + (shift_days * interval '1 day'),
        valid_from = valid_from + (shift_days * interval '1 day'),
        valid_to = valid_to + (shift_days * interval '1 day')
    WHERE source_type = 'seed';

    UPDATE tripsupport.country_status SET
        deadline_at = deadline_at + (shift_days * interval '1 day')
    WHERE source_type = 'seed';
END $$;

COMMIT;

-- Contrôle : ce que la journée contient maintenant.
SELECT (std AT TIME ZONE 'UTC')::date        AS jour,
       count(*)                              AS etapes,
       min(to_char(std AT TIME ZONE 'UTC', 'HH24:MI')) AS premiere,
       max(to_char(std AT TIME ZONE 'UTC', 'HH24:MI')) AS derniere,
       count(*) FILTER (WHERE out_at IS NOT NULL)      AS deja_parties,
       count(*) FILTER (WHERE std > now())             AS a_venir
FROM ops.legs
GROUP BY 1
ORDER BY 1;
