-- ============================================================
--  V63 — La « photo a H-1 » d'un depart, pour apprendre plus tard
--
--  Une ligne par etape, prise une heure avant le depart programme
--  (netplus.learning.lead), figee a cet instant : ce que l'on savait,
--  et seulement cela. Le retard de la jambe precedente n'y entre que
--  si l'avion etait deja pose ; le METAR retenu est le dernier publie
--  avant l'instant de la photo, perime au-dela de deux heures. Rien
--  n'est recalcule apres coup : c'est la regle du point-in-time, qui
--  separe un modele utilisable d'un modele flatteur.
--
--  Le resultat (heure bloc reelle, retard, cible a 15 min, code de
--  retard saisi) est ajoute plus tard, quand l'etape est partie ou
--  annulee. Tant que outcome_at est NULL, la ligne attend.
--
--  Cette table n'alimente aucun ecran : elle est la matiere d'un
--  futur modele de retard, entraine chez l'exploitant sur ses propres
--  vols (voir RAPPORT_CORRECTION.md, module 3).
-- ============================================================
CREATE TABLE ops.departure_snapshots (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    source_type           text        NOT NULL DEFAULT 'engine',
    source_ref            text,
    source_version        text,
    source_author         uuid,
    source_at             timestamptz NOT NULL DEFAULT now(),

    leg_id                uuid        NOT NULL REFERENCES ops.legs (id),
    taken_at              timestamptz NOT NULL,
    lead_minutes          integer     NOT NULL,

    -- l'etape telle qu'elle etait programmee a cet instant
    flight_no             text,
    registration          text,
    icao_type             text,
    dep_icao              text        NOT NULL,
    arr_icao              text        NOT NULL,
    std                   timestamptz NOT NULL,
    sta                   timestamptz NOT NULL,

    -- rotation : de loin les variables les plus predictives
    leg_index             integer,
    sched_turnaround_min  integer,
    inbound_known         boolean     NOT NULL DEFAULT false,
    inbound_delay_min     integer,

    -- congestion : mouvements PROGRAMMES a +/- 30 min, aucune fuite possible
    dep_congestion        integer     NOT NULL DEFAULT 0,
    arr_congestion        integer     NOT NULL DEFAULT 0,

    -- les faits que VIGIL lit, tels qu'ils etaient
    permits_total         integer,
    permits_outstanding   integer,
    services_total        integer,
    services_confirmed    integer,
    services_readiness    text,
    crew_complete         boolean,
    crew_ftl_status       text,
    crew_document_status  text,
    mel_open              integer     NOT NULL DEFAULT 0,
    mel_blocking          boolean     NOT NULL DEFAULT false,
    risk_level            text,
    risk_index            integer,

    -- meteo au depart : dernier METAR avant la photo, perime au-dela de 2 h
    dep_wx_observed_at    timestamptz,
    dep_wx_age_min        integer,
    dep_wind_dir_deg      integer,
    dep_wind_kt           integer,
    dep_wind_gust_kt      integer,
    dep_visibility_m      integer,
    dep_ceiling_ft        integer,
    dep_cavok             boolean,
    dep_conditions        text,
    dep_flight_category   text,
    dep_wx_raw            text,

    -- meteo a l'arrivee, meme regle
    arr_wx_observed_at    timestamptz,
    arr_wx_age_min        integer,
    arr_wind_dir_deg      integer,
    arr_wind_kt           integer,
    arr_wind_gust_kt      integer,
    arr_visibility_m      integer,
    arr_ceiling_ft        integer,
    arr_cavok             boolean,
    arr_conditions        text,
    arr_flight_category   text,
    arr_wx_raw            text,

    -- le resultat, ajoute quand il est connu
    out_at                timestamptz,
    dep_delay_min         integer,
    target_delay15        boolean,
    delay_code            text,
    cancelled             boolean     NOT NULL DEFAULT false,
    outcome_at            timestamptz,

    CONSTRAINT uq_departure_snapshots_leg UNIQUE (tenant_id, leg_id)
);

CREATE INDEX ix_departure_snapshots_open
    ON ops.departure_snapshots (tenant_id)
    WHERE outcome_at IS NULL;

CREATE INDEX ix_departure_snapshots_std
    ON ops.departure_snapshots (tenant_id, std);

COMMENT ON TABLE ops.departure_snapshots IS
    'Photo a H-1 de chaque depart (point-in-time), completee par le resultat : matiere du futur modele de retard.';
