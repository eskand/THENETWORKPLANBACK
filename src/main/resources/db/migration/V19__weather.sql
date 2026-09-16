-- ============================================================
--  V19 — Observations météo (METAR / SPECI).
--
--  Le prototype affichait une météo sans source, ou pas de météo
--  du tout ; l'annexe A4 prévoit un relais AVWX dont le jeton
--  était en clair dans le fichier (constat n°1 de l'audit).
--
--  Ici : une observation est une LIGNE, avec son texte brut, sa
--  source, l'heure de l'observation ET l'heure de réception. Un
--  groupe absent du METAR est NULL — jamais zéro, jamais une
--  valeur par défaut. La colonne raw_text permet de tout
--  recontrôler à la main, ce qui est la seule garantie sérieuse
--  face à un décodeur.
-- ============================================================

CREATE TABLE refdata.weather_observations (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    source_type       text        NOT NULL DEFAULT 'integration',
    source_ref        text,
    source_version    text,
    source_author     uuid,
    source_at         timestamptz NOT NULL DEFAULT now(),
    station_icao      text        NOT NULL,
    report_type       text        NOT NULL DEFAULT 'METAR',
    observed_at       timestamptz NOT NULL,
    received_at       timestamptz NOT NULL DEFAULT now(),
    raw_text          text        NOT NULL,
    provider          text        NOT NULL,
    -- Groupes décodés. Tous nullables : ce que le message ne dit pas,
    -- la base ne le dit pas non plus.
    wind_dir_deg      integer,
    wind_variable     boolean     NOT NULL DEFAULT false,
    wind_speed_kt     integer,
    wind_gust_kt      integer,
    visibility_m      integer,
    cavok             boolean     NOT NULL DEFAULT false,
    ceiling_ft        integer,
    temperature_c     integer,
    dewpoint_c        integer,
    qnh_hpa           integer,
    conditions        text,
    flight_category   text,
    CONSTRAINT uq_observation UNIQUE (station_icao, report_type, observed_at),
    CONSTRAINT ck_observation_type CHECK (report_type IN ('METAR', 'SPECI', 'TAF')),
    CONSTRAINT ck_observation_provider CHECK (provider IN ('NOAA', 'AVWX', 'MANUAL', 'ACARS')),
    CONSTRAINT ck_observation_wind_dir CHECK (wind_dir_deg IS NULL OR wind_dir_deg BETWEEN 0 AND 360),
    CONSTRAINT ck_observation_category CHECK (flight_category IS NULL OR flight_category IN ('VFR', 'MVFR', 'IFR', 'LIFR'))
);

CREATE INDEX ix_observations_station_time
    ON refdata.weather_observations (station_icao, observed_at DESC);

COMMENT ON COLUMN refdata.weather_observations.observed_at IS
    'Heure du message (groupe ddhhmmZ). received_at est l heure a laquelle nous l avons appris : l ecart est la latence, et un OCC en a besoin.';

COMMENT ON COLUMN refdata.weather_observations.flight_category IS
    'VFR / MVFR / IFR / LIFR, calcule du plafond et de la visibilite au moment du decodage, et stocke avec eux.';
