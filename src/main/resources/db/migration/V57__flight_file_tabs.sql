-- ============================================================
--  V57 — Flight file: the three tabs that had nowhere to write
--
--  L'annexe A4 tient les onglets FUEL, PAX et TRIP FOLDER du
--  dossier de vol dans le navigateur : un prix carburant importe
--  en CSV sous `localStorage['netplus.fuelprices.v1']`, une liste
--  de passagers dans `flight._pax`, et cinq pastilles de documents
--  ecrites en dur dans le HTML (« On File », « Pending »). Rien de
--  tout cela ne survit a la fermeture de l'onglet, et deux agents
--  sur deux postes ne voient pas le meme dossier.
--
--  Les trois tables ci-dessous sont ce qu'il faut pour que ces
--  onglets disent la verite : un tarif par escale et par
--  fournisseur, une ligne par passager avec son document de
--  voyage, une ligne par document du dossier de vol.
-- ============================================================

-- ------------------------------------------------------------
--  Carburant — le tarif d'une escale chez un fournisseur.
--
--  L'annexe importe un fichier CSV par fournisseur ; la table
--  garde la meme granularite (fournisseur x aerodrome) et y ajoute
--  ce que le CSV portait deja sans le stocker : la fenetre de
--  validite. Un tarif sans date de fin est un tarif qu'on affiche
--  six mois apres son expiration.
-- ------------------------------------------------------------
CREATE TABLE tripsupport.fuel_prices (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    station_icao     text        NOT NULL,
    supplier_name    text        NOT NULL,
    fuel_grade       text        NOT NULL DEFAULT 'JET A-1',
    price            numeric(12, 4) NOT NULL,
    unit             text        NOT NULL DEFAULT 'USG',
    currency         text        NOT NULL DEFAULT 'USD',
    fees             text,
    effective_from   date        NOT NULL DEFAULT CURRENT_DATE,
    effective_to     date,
    CONSTRAINT uq_fuel_price UNIQUE (tenant_id, station_icao, supplier_name, fuel_grade, effective_from),
    CONSTRAINT ck_fuel_unit CHECK (unit IN ('USG', 'LITER')),
    CONSTRAINT ck_fuel_price_positive CHECK (price > 0),
    CONSTRAINT ck_fuel_window CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_fuel_prices_station ON tripsupport.fuel_prices (tenant_id, station_icao, effective_from DESC);

COMMENT ON COLUMN tripsupport.fuel_prices.unit IS
    'USG or LITER — the unit the supplier quotes in, never converted before storage';

-- ------------------------------------------------------------
--  Passagers — une ligne par passager, avec son document.
--
--  L'annexe fusionne la liste des passagers et la section « ID
--  Documents » en un seul tableau (TNPPaxDocs) : c'est la bonne
--  forme, parce qu'un passager sans document valide n'embarque pas,
--  et la table la reprend. La validite n'est pas stockee : elle se
--  deduit de la date d'expiration et de la date du vol, et un
--  booleen fige serait faux le lendemain.
-- ------------------------------------------------------------
CREATE TABLE ops.leg_passengers (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    leg_id           uuid        NOT NULL REFERENCES ops.legs (id) ON DELETE CASCADE,
    seq              integer     NOT NULL,
    surname          text        NOT NULL,
    given_name       text,
    document_type    text,
    document_number  text,
    document_expiry  date,
    nationality      text,
    date_of_birth    date,
    checked_in       boolean     NOT NULL DEFAULT false,
    special_request  text,
    CONSTRAINT uq_leg_passenger_seq UNIQUE (leg_id, seq),
    CONSTRAINT ck_pax_document_type CHECK (document_type IS NULL OR document_type IN (
        'PASSPORT', 'NATIONAL_ID', 'RESIDENCE_PERMIT', 'VISA',
        'CREW_CERTIFICATE', 'LAISSEZ_PASSER', 'OTHER'))
);

CREATE INDEX ix_leg_passengers_leg ON ops.leg_passengers (leg_id, seq);

-- ------------------------------------------------------------
--  Dossier de vol — les documents que l'equipage depose.
--
--  Les cinq pastilles de l'annexe etaient ecrites en dur : « Flight
--  Plan — On File » s'affichait sur un vol dont personne n'avait
--  depose de plan de vol. Une ligne existe ici quand le document
--  existe, et la pastille est « On File » pour cette raison.
--
--  Le contenu est garde en base. C'est defendable a cette taille —
--  un OFP fait quelques centaines de kilo-octets, la limite est
--  posee par le service — et surtout c'est ce qui rend le dossier
--  transactionnel : un document et l'etape qu'il documente sont
--  sauvegardes, restaures et purges ensemble.
-- ------------------------------------------------------------
CREATE TABLE ops.leg_documents (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    source_type      text        NOT NULL DEFAULT 'manual',
    source_ref       text,
    source_version   text,
    source_author    uuid,
    source_at        timestamptz NOT NULL DEFAULT now(),
    leg_id           uuid        NOT NULL REFERENCES ops.legs (id) ON DELETE CASCADE,
    kind             text        NOT NULL,
    file_name        text        NOT NULL,
    content_type     text        NOT NULL,
    size_bytes       bigint      NOT NULL,
    content          bytea       NOT NULL,
    uploaded_at      timestamptz NOT NULL DEFAULT now(),
    uploaded_by      uuid,
    remark           text,
    CONSTRAINT uq_leg_document UNIQUE (leg_id, kind),
    CONSTRAINT ck_leg_document_kind CHECK (kind IN (
        'FPL', 'OFP', 'WEIGHT_BALANCE', 'NOTOC', 'FUEL_RECEIPT', 'GENDEC', 'OTHER')),
    CONSTRAINT ck_leg_document_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_leg_documents_leg ON ops.leg_documents (leg_id);

COMMENT ON TABLE ops.leg_documents IS
    'Trip folder of a leg — one row per document actually deposited';
