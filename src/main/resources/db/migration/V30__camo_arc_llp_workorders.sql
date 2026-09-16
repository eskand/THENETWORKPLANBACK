-- ============================================================
--  V30 — les trois choses que CAMO ne savait pas dire.
--
--  POURQUOI. L'ecran CAMO de l'annexe A4 tient sur trois onglets
--  — Fleet, AD/SB, Life-Limited Parts — et sur un dossier de
--  navigabilite a droite. Deux des trois onglets et la moitie du
--  dossier n'avaient aucune table derriere eux :
--
--    * le CERTIFICAT D'EXAMEN DE NAVIGABILITE (ARC) : numero, date
--      d'emission, date d'echeance, base de l'examen, examinateur,
--      renvoi au CofA, et l'historique des certificats precedents.
--      camo.aircraft ne portait que next_check_* : de quoi annoncer
--      une visite, pas de quoi dire si l'appareil a le droit de
--      voler ;
--    * les PIECES A VIE LIMITEE (LLP) : un disque de turbine, un axe
--      de train. Ce sont elles qui immobilisent un appareil sans
--      qu'aucune visite ne soit due ;
--    * les ORDRES DE TRAVAIL : ou la visite est faite, chez qui, et
--      dans quel etat elle est.
--
--  CE QUI N'EST PAS STOCKE, ET POURQUOI. Ni les jours restants, ni
--  le pourcentage de vie restante. Le prototype les avait figes a
--  l'ecriture (arcDays), et son propre code a du les recalculer par
--  dessus quand ils ont derive de quatorze jours. Un compteur qui ne
--  bouge pas ment tot ou tard : on garde la date et la limite, on
--  derive le reste a la lecture.
--
--  L'HISTORIQUE EST FAIT DE LIGNES. Un ARC renouvele ne remplace pas
--  le precedent : il le PERIME. superseded_at NULL designe celui en
--  vigueur, et les autres restent lisibles. C'est la meme discipline
--  que les versions de roster : ce qui a ete certifie un jour doit
--  pouvoir etre relu tel quel.
-- ============================================================

-- ------------------------------------------------------------
--  Certificats d'examen de navigabilite
-- ------------------------------------------------------------
CREATE TABLE camo.airworthiness_reviews (
    id                   uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    source_type          text        NOT NULL DEFAULT 'manual',
    source_ref           text,
    source_version       text,
    source_author        uuid,
    source_at            timestamptz NOT NULL DEFAULT now(),
    aircraft_id          uuid        NOT NULL REFERENCES camo.aircraft (id),
    certificate_no       text        NOT NULL,
    issued_on            date        NOT NULL,
    expires_on           date        NOT NULL,
    -- M.A.710(a) examen complet, M.A.711(c) prorogation, recommandation
    -- adressee a l'autorite. Trois bases, trois portees differentes.
    review_basis         text        NOT NULL DEFAULT 'FULL',
    reviewed_by          text,
    reviewer_approval_no text,
    -- Le certificat de navigabilite auquel l'ARC se rattache. L'ARC
    -- n'existe pas seul : il atteste que le CofA reste valable.
    cofa_ref             text,
    -- NULL = celui en vigueur. Renseigne = remplace par un plus recent.
    superseded_at        timestamptz,
    remark               text,
    CONSTRAINT uq_arc_certificate UNIQUE (tenant_id, certificate_no),
    CONSTRAINT ck_arc_window CHECK (expires_on > issued_on),
    CONSTRAINT ck_arc_basis CHECK (review_basis IN ('FULL', 'EXTENSION', 'RECOMMENDATION'))
);

-- Un seul ARC en vigueur par appareil : l'index partiel le rend
-- impossible a violer, plutot que de le verifier dans le service.
CREATE UNIQUE INDEX uq_arc_in_force
    ON camo.airworthiness_reviews (aircraft_id)
    WHERE superseded_at IS NULL;

CREATE INDEX ix_arc_expiry
    ON camo.airworthiness_reviews (tenant_id, expires_on)
    WHERE superseded_at IS NULL;

-- ------------------------------------------------------------
--  Pieces a vie limitee
-- ------------------------------------------------------------
CREATE TABLE camo.life_limited_parts (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),
    aircraft_id    uuid        NOT NULL REFERENCES camo.aircraft (id),
    name           text        NOT NULL,
    part_no        text,
    serial_no      text,
    -- Ou la piece est montee : ENG1, ENG2, MLG-L, APU. Deux disques
    -- du meme numero de piece n'ont pas la meme vie consommee.
    position       text,
    -- Les trois limites possibles. Une piece en porte au moins une ;
    -- beaucoup en portent deux, et c'est la plus proche qui compte.
    limit_hours    numeric(10,2),
    limit_cycles   integer,
    limit_months   integer,
    used_hours     numeric(10,2) NOT NULL DEFAULT 0,
    used_cycles    integer       NOT NULL DEFAULT 0,
    installed_on   date,
    -- Renseigne quand la piece est deposee : la ligne reste, elle
    -- sort seulement du calcul.
    removed_at     timestamptz,
    remark         text,
    CONSTRAINT ck_llp_has_limit CHECK (
        limit_hours IS NOT NULL OR limit_cycles IS NOT NULL OR limit_months IS NOT NULL),
    CONSTRAINT ck_llp_positive CHECK (
        used_hours >= 0 AND used_cycles >= 0
        AND (limit_hours  IS NULL OR limit_hours  > 0)
        AND (limit_cycles IS NULL OR limit_cycles > 0)
        AND (limit_months IS NULL OR limit_months > 0))
);

CREATE INDEX ix_llp_aircraft
    ON camo.life_limited_parts (tenant_id, aircraft_id)
    WHERE removed_at IS NULL;

-- ------------------------------------------------------------
--  Ordres de travail
-- ------------------------------------------------------------
CREATE TABLE camo.work_orders (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),
    aircraft_id    uuid        NOT NULL REFERENCES camo.aircraft (id),
    order_no       text        NOT NULL,
    title          text        NOT NULL,
    status         text        NOT NULL DEFAULT 'SCHEDULED',
    -- L'atelier, en clair, et son terrain. Le terrain sert a dire si
    -- l'appareil doit etre convoye pour la visite.
    facility       text,
    facility_icao  text,
    opened_on      date,
    target_on      date,
    -- Une cible qui n'est pas une date en est une information quand meme :
    -- « ASAP — AOG », « parts on order ». Le prototype les ecrivait dans le
    -- meme champ que les dates ; ici la date reste une date, et le reste est
    -- dit a cote. Un ordre sans date cible se voit, au lieu de se ranger a
    -- une echeance inventee.
    target_note    text,
    closed_on      date,
    labour_hours   numeric(10,2),
    remark         text,
    CONSTRAINT uq_work_order_no UNIQUE (tenant_id, order_no),
    CONSTRAINT ck_work_order_status CHECK (status IN (
        'DRAFT', 'SCHEDULED', 'IN_WORK', 'AWAITING_PARTS', 'CLOSED', 'CANCELLED'))
);

CREATE INDEX ix_work_orders_open
    ON camo.work_orders (tenant_id, aircraft_id)
    WHERE closed_on IS NULL;
