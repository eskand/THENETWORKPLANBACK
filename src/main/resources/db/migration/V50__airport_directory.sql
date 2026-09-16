-- ============================================================
--  V50 — l'annuaire des aerodromes.
--
--  POURQUOI. refdata.airports tient vingt-six aerodromes : ceux que
--  l'exploitant dessert. L'annuaire de l'annexe A4 en tient neuf mille
--  cinq cent quatre-vingt-trois, et c'est le point : on consulte un
--  annuaire pour un terrain ou l'on NE VA PAS encore. Un deroutement
--  se prepare sur un aerodrome qui n'est dans aucun programme.
--
--  CE QUI S'AJOUTE A LA FICHE. Le prototype affiche, pour chaque
--  terrain : la region, le niveau de trafic, le carburant disponible,
--  la categorie de lutte contre l'incendie, les horaires, le regime de
--  creneaux, les restrictions et une ligne de piste en clair. Rien de
--  cela n'existait ici — l'ecran ne pouvait donc pas les montrer.
--
--  LES FREQUENCES. Table a part, pas trois colonnes : une tour peut
--  en avoir deux, et « 118.100, 119.700 » dans une colonne est une
--  liste qu'il faudra redecouper a chaque lecture.
--
--  LES SERVICES. refdata, pas tripsupport. tripsupport.suppliers est
--  la liste des fournisseurs SOUS CONTRAT de l'exploitant — elle porte
--  un contract_ref et un drapeau preferred. Ce qui arrive ici est un
--  annuaire : qui assiste a Bou Saada, que l'on y aille ou non. Les
--  confondre aurait fait croire a treize mille contrats.
-- ============================================================

-- ------------------------------------------------------------
--  1. La fiche d'aerodrome
-- ------------------------------------------------------------
ALTER TABLE refdata.airports
    -- 1 a 7, la decoupe regionale de l'annuaire. Sert au filtre par
    -- region, qui est la seule facon de parcourir neuf mille terrains.
    ADD COLUMN region           smallint,
    ADD COLUMN traffic_level    text,
    ADD COLUMN fuel_type        text,
    -- La categorie OACI de lutte contre l'incendie, telle qu'elle est
    -- publiee : « 9 », « 7 », parfois « 5/7 ». Du texte, pas un entier.
    ADD COLUMN fire_category    text,
    ADD COLUMN operating_hours  text,
    ADD COLUMN slot_required    boolean NOT NULL DEFAULT false,
    -- « SCR LEVEL 3 », « SMA » : le regime, quand il y en a un.
    ADD COLUMN slot_regime      text,
    ADD COLUMN restrictions     text,
    -- La ligne de pistes telle que l'AIP la publie, quand la geometrie
    -- detaillee n'est pas saisie. Les deux coexistent : refdata.runways
    -- porte le detail des terrains desservis, ceci porte le reste.
    ADD COLUMN runway_remark    text;

ALTER TABLE refdata.airports
    ADD CONSTRAINT ck_airports_region CHECK (region IS NULL OR region BETWEEN 1 AND 7),
    ADD CONSTRAINT ck_airports_traffic CHECK (
        traffic_level IS NULL OR traffic_level IN ('LOW', 'MEDIUM', 'HIGH'));

CREATE INDEX ix_airports_region ON refdata.airports (region);
CREATE INDEX ix_airports_name ON refdata.airports (name);

-- ------------------------------------------------------------
--  2. Les frequences
-- ------------------------------------------------------------
CREATE TABLE refdata.airport_frequencies (
    id          uuid    PRIMARY KEY DEFAULT gen_random_uuid(),
    airport_id  uuid    NOT NULL REFERENCES refdata.airports (id) ON DELETE CASCADE,
    -- TOWER, ATIS, GROUND. Une tour peut en avoir deux : d'ou une
    -- ligne par frequence, et non une colonne par service.
    service     text    NOT NULL,
    mhz         text    NOT NULL,
    sort_order  integer NOT NULL DEFAULT 0,
    CONSTRAINT ck_frequency_service CHECK (service IN ('TOWER', 'ATIS', 'GROUND', 'APPROACH')),
    CONSTRAINT uq_airport_frequency UNIQUE (airport_id, service, mhz)
);

CREATE INDEX ix_frequencies_airport ON refdata.airport_frequencies (airport_id);

-- ------------------------------------------------------------
--  3. L'annuaire des services
-- ------------------------------------------------------------
CREATE TABLE refdata.airport_services (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'refdata',
    source_ref     text,
    source_at      timestamptz NOT NULL DEFAULT now(),

    airport_id   uuid NOT NULL REFERENCES refdata.airports (id) ON DELETE CASCADE,
    -- FBO, HANDLING, TRIP_SUPPORT, FUEL, SUPERVISORY, CATERING,
    -- AUTHORITY. Les six categories de l'annexe A4, plus les contacts
    -- de l'aerodrome lui-meme.
    service_type text NOT NULL,
    name         text NOT NULL,
    phone        text,
    after_hours  text,
    fax          text,
    email        text,
    website      text,
    -- « H24 », « 0600-2200 ». Un service ferme la nuit ne se decouvre
    -- pas a deux heures du matin.
    hours        text,
    -- Les marques de carburant portees : « TOTAL; AEG FUELS ».
    fuel_brands  text,
    frequency    text,

    CONSTRAINT ck_airport_service_type CHECK (service_type IN (
        'FBO', 'HANDLING', 'TRIP_SUPPORT', 'FUEL', 'SUPERVISORY', 'CATERING', 'AUTHORITY')),
    -- Un service sans nom n'est pas un service : c'est une ligne vide
    -- dans un annuaire qu'on consulte pour appeler quelqu'un.
    CONSTRAINT ck_airport_service_name CHECK (length(btrim(name)) > 0)
);

CREATE INDEX ix_airport_services ON refdata.airport_services (airport_id, service_type);
