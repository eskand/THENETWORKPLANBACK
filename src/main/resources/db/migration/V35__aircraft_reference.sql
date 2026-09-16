-- ============================================================
--  V35 — la base avion unique.
--
--  POURQUOI. L'ecran Database du prototype (annexe A4, viewDatabase
--  l. 5996) affiche un registre de flotte dont chaque colonne — MTOW,
--  passagers, distance franchissable, croisiere, piste minimale — est
--  lue dans UNE table de reference de 308 fiches, que le fichier
--  nomme lui-meme « la base avion unique de l'application » et qui
--  remplace chez lui six tables qui coexistaient et se contredisaient
--  (TNP_ACDB v1, AIRCRAFT_DB, DB_TYPE_SPEC, NETPLUS_MTOW_BY_TYPE,
--  AIRCRAFT_MTOW_KG, MIN_RWY_BY_TYPE).
--
--  Ici refdata.aircraft_types portait neuf lignes : les types que
--  l'exploitant possede. C'est autre chose qu'une reference. Avec
--  elles seules, un devis commercial sur un type qu'on ne possede pas
--  n'a rien a lire, et l'audit de suitability d'un terrain non plus.
--
--  LES DEUX TABLES, ET CE QUI LES SEPARE.
--
--    aircraft_reference — ce qui est VRAI d'un type, d'ou qu'il vienne.
--      308 fiches, cle (constructeur, modele). Pas cle sur le code
--      OACI : 25 codes portent plusieurs modeles (F2TH couvre Falcon
--      2000 et Falcon 2000LXS, qui ne decollent pas sur la meme
--      longueur) et cinq fiches n'ont pas de code du tout. Une cle
--      OACI unique aurait donc perdu 31 fiches en silence.
--
--    aircraft_types — ce que l'exploitant EXPLOITE. Garde sa cle OACI
--      unique, garde ses colonnes, et pointe desormais vers sa fiche
--      de reference. Rien de ce qui la lit ne change.
--
--  LA PISTE MINIMALE EST LA TODA AU MTOW. C'est la regle du prototype,
--  ecrite dans son code : « la piste mini EST la TODA au MTOW ». On
--  stocke donc la distance de decollage en metres, et min_runway_ft
--  en devient la conversion — une seule valeur, deux unites, pas deux
--  sources qui derivent.
--
--  CE QUI N'EST PAS REPRIS. Envergure, longueur, hauteur et RECAT : le
--  prototype ne les tient que pour 193 des 308 fiches, rien ici ne les
--  lit encore, et une colonne vide aux deux tiers ne rend service a
--  personne. Elles s'ajouteront le jour ou un calcul de poste de
--  stationnement les demandera.
-- ============================================================

CREATE TABLE refdata.aircraft_reference (
    id                     uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              uuid,
    created_at             timestamptz NOT NULL DEFAULT now(),
    updated_at             timestamptz NOT NULL DEFAULT now(),
    source_type            text        NOT NULL DEFAULT 'refdata',
    source_ref             text,
    source_version         text,
    source_author          uuid,
    source_at              timestamptz NOT NULL DEFAULT now(),

    manufacturer           text        NOT NULL,
    model                  text        NOT NULL,
    -- Nullable : cinq fiches sur 308 n'ont pas de designateur OACI.
    icao_type              text,
    -- Le code descriptif du Doc 8643 : L2J = leger, 2 reacteurs.
    descriptor             text,

    -- L / M / H. Calculee de la MTOW quand le Doc 8643 ne la donne pas,
    -- et wake_source dit laquelle des deux on lit : la categorie de
    -- turbulence de sillage EST definie par la masse (L <= 7 t,
    -- M de 7 a 136 t, H >= 136 t), donc la calculer n'invente rien.
    wake_category          text,
    wake_source            text,

    mtow_kg                integer,
    mzfw_kg                integer,
    mlw_kg                 integer,
    -- Masse a vide en ordre d'exploitation : ce que l'appareil pese avant
    -- le premier passager et le premier kilo de carburant.
    dow_kg                 integer,
    fuel_kg                integer,
    fuel_l                 integer,

    -- Les sieges installes, et les personnes a bord selon le Doc 8643.
    -- Les deux ne disent pas la meme chose : un Falcon 2000 a dix sieges
    -- installes et un maximum de dix-neuf personnes a bord. La chaine
    -- « 15-18 » ou « 2+9 » est gardee telle quelle, et la borne haute est
    -- derivee a la lecture par une regle unique.
    seats                  integer,
    pob                    text,

    -- Quatre portees, parce qu'une seule mentirait : a charge marchande
    -- maximale on va moins loin qu'a plein carburant, et la valeur
    -- affichee par defaut est celle a pleins passagers.
    range_nm               integer,
    range_max_payload_nm   integer,
    range_full_pax_nm      integer,
    range_max_fuel_nm      integer,

    -- Null pour un turbopropulseur : il ne se croise pas en Mach.
    mach                   numeric(4,3),
    cruise_tas_kt          integer,
    optimum_fl             integer,

    -- Distance de decollage au MTOW, en metres. C'est la piste minimale.
    takeoff_distance_m     integer,
    rffs_category          integer,
    -- 60, 120, 180 : les seuils ETOPS approuvables. Vide = non applicable.
    etops_minutes          integer[]   NOT NULL DEFAULT '{}',

    -- L'unicite est plus bas : elle porte aussi sur le code OACI, et une
    -- contrainte de table ne peut pas traiter les codes absents comme egaux.
    -- 'L-M' n'est pas une faute de saisie : le Doc 8643 laisse deux types
    -- a cheval sur la limite des 7 t, leur categorie dependant de la
    -- variante. La garder telle quelle vaut mieux que trancher a leur place.
    CONSTRAINT ck_aircraft_reference_wake CHECK (
        wake_category IS NULL OR wake_category IN ('L', 'M', 'H', 'J', 'L-M')),
    CONSTRAINT ck_aircraft_reference_mass CHECK (
        (mtow_kg IS NULL OR mtow_kg > 0)
        AND (mzfw_kg IS NULL OR mtow_kg IS NULL OR mzfw_kg <= mtow_kg)
        AND (mlw_kg  IS NULL OR mtow_kg IS NULL OR mlw_kg  <= mtow_kg))
);

-- Une fiche est identifiee par son constructeur, son modele ET son
-- designateur. Le modele seul ne suffit pas : la source appelle « BAe 146-100 »
-- deux appareils differents, le B461 de 37,3 t et le RJ70 de 43,1 t, et les
-- confondre en aurait fait disparaitre trois en silence. COALESCE traite les
-- cinq fiches sans designateur comme comparables entre elles, ce qu'une
-- contrainte UNIQUE ordinaire ne fait pas : deux NULL n'y sont jamais egaux.
CREATE UNIQUE INDEX uq_aircraft_reference
    ON refdata.aircraft_reference (manufacturer, model, COALESCE(icao_type, ''));

-- Le code OACI n'est pas unique a lui seul, mais c'est par lui qu'on cherche.
CREATE INDEX ix_aircraft_reference_icao ON refdata.aircraft_reference (icao_type);
CREATE INDEX ix_aircraft_reference_model ON refdata.aircraft_reference (lower(model));

-- ------------------------------------------------------------
--  Ce que l'exploitant possede pointe vers ce qui est vrai du type.
-- ------------------------------------------------------------
ALTER TABLE refdata.aircraft_types
    ADD COLUMN reference_id uuid REFERENCES refdata.aircraft_reference (id),
    -- La piste minimale devient metrique, comme la source. Les pieds
    -- restent, convertis, pour le code qui les lit deja.
    ADD COLUMN min_runway_m integer,
    -- Ce que le registre de flotte affiche et que la reference ne porte
    -- pas : la motorisation, et la composition d'equipage certifiee.
    ADD COLUMN engines text,
    ADD COLUMN crew_flight_deck smallint,
    ADD COLUMN crew_cabin smallint,
    ADD COLUMN crew_certification text;

COMMENT ON COLUMN refdata.aircraft_types.min_runway_m IS
    'Distance de decollage au MTOW. min_runway_ft en est la conversion.';
