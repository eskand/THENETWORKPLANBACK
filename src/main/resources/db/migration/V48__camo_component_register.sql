-- ============================================================
--  V48 — le registre des composants, les documents, la piste d'audit.
--
--  POURQUOI. L'administration CAMO de l'annexe A4 tient vingt et une
--  destinations. Treize ont deja leur table ici. Il en manque trois,
--  et ce sont celles qu'un auditeur ouvre en premier.
--
--  LES COMPOSANTS. Le prototype tient quatre collections separees —
--  moteurs, APU, atterrisseurs, composants — avec les memes champs :
--  un P/N, un S/N, une position, un TSN/TSO, une date de pose, une
--  limite. Ce sont quatre VUES sur une meme chose. Une seule table,
--  avec une categorie : un moteur EST un composant, et quatre tables
--  auraient fini par diverger sur ce qu'est un TSO.
--
--  LES DOCUMENTS. ARC, CDN, immatriculation, assurance, licence
--  radio, certificat acoustique, masse et centrage, APRS, AMM, IPC,
--  SRM, MEL, CDL, AMP. Chacun a une date d'emission et une date
--  d'expiration, et un document expire immobilise l'appareil aussi
--  surement qu'une panne. Les jours restants ne sont PAS stockes :
--  ils se calculent a la lecture, sinon ils sont faux des demain.
--
--  LA PISTE D'AUDIT. Elle enregistre qui a change quoi, quand, et
--  quelle etait la valeur precedente. Sans la valeur precedente ce
--  n'est pas une piste d'audit, c'est une liste d'evenements.
-- ============================================================

-- ------------------------------------------------------------
--  1. Le registre des composants
-- ------------------------------------------------------------
CREATE TABLE camo.components (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    -- ENGINE, APU, GEAR, COMPONENT. Quatre ecrans, une table.
    category      text NOT NULL,
    aircraft_id   uuid REFERENCES camo.aircraft (id),
    name          text NOT NULL,
    ata_chapter   text,
    part_number   text,
    serial_number text,
    -- « Engine 1 », « MLG-L ». Vide pour un composant sans position fixe.
    position      text,

    install_date    date,
    install_hours   numeric(10, 2),
    install_cycles  integer,
    removal_date    date,

    tsn numeric(10, 2),
    csn integer,
    tso numeric(10, 2),
    cso integer,

    life_limit_hours   numeric(10, 2),
    life_limit_cycles  integer,
    -- La limite calendaire : certaines pieces se deposent a une date,
    -- quelles que soient les heures.
    calendar_limit     date,

    overhaul_due        date,
    overhaul_due_hours  numeric(10, 2),
    next_inspection     date,
    next_inspection_hours numeric(10, 2),

    -- Propre au moteur : la marge EGT et la consommation d'huile sont
    -- les deux tendances qu'un CAMO suit reellement.
    egt_margin       numeric(6, 1),
    oil_consumption  numeric(6, 3),

    status text NOT NULL DEFAULT 'INSTALLED',
    notes  text,

    CONSTRAINT ck_component_category CHECK (category IN ('ENGINE', 'APU', 'GEAR', 'COMPONENT')),
    CONSTRAINT ck_component_status CHECK (
        status IN ('INSTALLED', 'REMOVED', 'SERVICEABLE', 'UNSERVICEABLE', 'SCRAPPED')),
    -- Un composant pose porte une date de pose. Sans elle, le TSO ne
    -- veut rien dire : il compte depuis quand ?
    CONSTRAINT ck_component_installed CHECK (
        status <> 'INSTALLED' OR (aircraft_id IS NOT NULL AND install_date IS NOT NULL)),
    -- Un composant depose n'est plus sur un avion a une date : la
    -- depose a sa date, ou elle n'a pas eu lieu.
    CONSTRAINT ck_component_removed CHECK (status <> 'REMOVED' OR removal_date IS NOT NULL),
    CONSTRAINT ck_component_dates CHECK (
        removal_date IS NULL OR install_date IS NULL OR removal_date >= install_date),
    CONSTRAINT uq_component_sn UNIQUE (tenant_id, part_number, serial_number)
);

CREATE INDEX ix_components_aircraft ON camo.components (tenant_id, aircraft_id, category);
CREATE INDEX ix_components_status ON camo.components (tenant_id, status);

-- ------------------------------------------------------------
--  2. Les documents
-- ------------------------------------------------------------
CREATE TABLE camo.documents (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid        NOT NULL REFERENCES platform.tenants (id),
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    source_type    text        NOT NULL DEFAULT 'manual',
    source_ref     text,
    source_version text,
    source_author  uuid,
    source_at      timestamptz NOT NULL DEFAULT now(),

    category     text NOT NULL,
    title        text NOT NULL,
    reference    text,
    aircraft_id  uuid REFERENCES camo.aircraft (id),
    component_id uuid REFERENCES camo.components (id),
    issue_date   date,
    -- NULL veut dire « sans echeance » (un AMM, un IPC), pas « inconnue ».
    expiry_date  date,
    issued_by    text,
    file_name    text,
    file_type    text,
    file_size    bigint,
    status       text NOT NULL DEFAULT 'CURRENT',
    notes        text,

    CONSTRAINT ck_document_status CHECK (status IN ('CURRENT', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT ck_document_dates CHECK (
        expiry_date IS NULL OR issue_date IS NULL OR expiry_date >= issue_date)
);

CREATE INDEX ix_documents_aircraft ON camo.documents (tenant_id, aircraft_id);
CREATE INDEX ix_documents_expiry ON camo.documents (tenant_id, expiry_date)
    WHERE expiry_date IS NOT NULL AND status = 'CURRENT';

-- ------------------------------------------------------------
--  3. La piste d'audit
-- ------------------------------------------------------------
CREATE TABLE platform.audit_events (
    id          bigserial   PRIMARY KEY,
    tenant_id   uuid        NOT NULL REFERENCES platform.tenants (id),
    at          timestamptz NOT NULL DEFAULT now(),

    -- La collection et la ligne touchees, en clair.
    entity      text NOT NULL,
    entity_id   text,
    -- Ce qu'on a lu sur l'ecran au moment du changement : « TS-NPA »,
    -- pas un uuid. Un audit lisible six mois plus tard.
    entity_label text,

    action      text NOT NULL,
    field       text,
    -- La valeur d'avant. Sans elle, ce n'est pas une piste d'audit.
    old_value   text,
    new_value   text,

    actor_id    uuid REFERENCES platform.users (id),
    actor_name  text,
    actor_role  text,
    reason      text,

    CONSTRAINT ck_audit_action CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'IMPORT', 'EXPORT'))
);

CREATE INDEX ix_audit_recent ON platform.audit_events (tenant_id, at DESC);
CREATE INDEX ix_audit_entity ON platform.audit_events (tenant_id, entity, entity_id);

-- ------------------------------------------------------------
--  4. L'autorite CAMO
-- ------------------------------------------------------------
--  Declarer un avion AOG appartient au SERVICE, pas a un grade : un
--  planificateur qui trouve une raison d'arreter un appareil doit
--  pouvoir l'arreter sans attendre un post holder. C'est le sens de
--  l'autorite. Le controle n'est pas la permission : c'est que chaque
--  changement porte un nom, une heure et une trace.
ALTER TABLE platform.users
    ADD COLUMN camo_role text,
    ADD COLUMN email     text;

ALTER TABLE platform.users
    ADD CONSTRAINT ck_users_camo_role CHECK (
        camo_role IS NULL OR camo_role IN (
            'CAMO_MANAGER', 'AIRWORTHINESS', 'ENGINEER', 'PLANNER',
            'TECHNICAL_RECORDS', 'OCC', 'SAFETY', 'OBSERVER'));

UPDATE platform.users SET camo_role = 'CAMO_MANAGER' WHERE role = 'CAMO';
UPDATE platform.users SET camo_role = 'OCC'          WHERE role IN ('DISPATCHER', 'OCC_MANAGER');
UPDATE platform.users SET camo_role = 'SAFETY'       WHERE role = 'SAFETY';
UPDATE platform.users SET camo_role = 'OBSERVER'     WHERE camo_role IS NULL;

-- ------------------------------------------------------------
--  5. Les documents de l'annexe A4
--
--  Le CDN, l'ARC en vigueur et la chaine des ARC precedents, par
--  immatriculation. Les precedents sont gardes en SUPERSEDED :
--  un audit remonte la chaine des prolongations, il ne se contente
--  pas du certificat du jour.
-- ------------------------------------------------------------
INSERT INTO camo.documents
    (tenant_id, source_type, source_ref, category, title, reference, aircraft_id,
     issue_date, expiry_date, issued_by, status, notes)
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPA', 'CofA N° TN-AOC-2019-0187', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPA' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPA', 'ARC/TS-NPA/2026-03', ac.id, to_date('13 Mar 2026', 'DD Mon YYYY'), to_date('12 Mar 2027', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPA' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPA', 'ARC/TS-NPA/2025-03', ac.id, to_date('13 Mar 2025', 'DD Mon YYYY'), to_date('12 Mar 2026', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPA' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPA', 'ARC/TS-NPA/2024-03', ac.id, to_date('13 Mar 2024', 'DD Mon YYYY'), to_date('12 Mar 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPA' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPK', 'CofA N° TN-AOC-2020-0234', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPK' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPK', 'ARC/TS-NPK/2026-01', ac.id, to_date('21 Jan 2026', 'DD Mon YYYY'), to_date('20 Jan 2027', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPK' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPK', 'ARC/TS-NPK/2025-01', ac.id, to_date('21 Jan 2025', 'DD Mon YYYY'), to_date('20 Jan 2026', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPK' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPC', 'CofA N° TN-AOC-2018-0129', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPC' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPC', 'ARC/TS-NPC/2025-11', ac.id, to_date('04 Nov 2025', 'DD Mon YYYY'), to_date('03 Nov 2026', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPC' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPC', 'ARC/TS-NPC/2024-11', ac.id, to_date('04 Nov 2024', 'DD Mon YYYY'), to_date('03 Nov 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPC' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPT', 'CofA N° TN-AOC-2021-0301', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPT' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPT', 'ARC/TS-NPT/2025-08', ac.id, to_date('15 Aug 2025', 'DD Mon YYYY'), to_date('14 Aug 2026', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPT' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPT', 'ARC/TS-NPT/2024-08', ac.id, to_date('15 Aug 2024', 'DD Mon YYYY'), to_date('14 Aug 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPT' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPQ', 'CofA N° TN-AOC-2017-0098', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPQ' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPQ', 'ARC/TS-NPQ/2025-12', ac.id, to_date('30 Dec 2025', 'DD Mon YYYY'), to_date('29 Dec 2026', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPQ' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPQ', 'ARC/TS-NPQ/2024-12', ac.id, to_date('30 Dec 2024', 'DD Mon YYYY'), to_date('29 Dec 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPQ' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPR', 'CofA N° TN-AOC-2022-0355', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPR' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPR', 'ARC/TS-NPR/2025-09', ac.id, to_date('08 Sep 2025', 'DD Mon YYYY'), to_date('07 Sep 2026', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPR' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPR', 'ARC/TS-NPR/2024-09', ac.id, to_date('08 Sep 2024', 'DD Mon YYYY'), to_date('07 Sep 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPR' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPB', 'CofA N° TN-AOC-2018-951', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPB' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPB', 'ARC/TS-NPB/2026-10', ac.id, to_date('30 Mar 2026', 'DD Mon YYYY'), to_date('30 Mar 2027', 'DD Mon YYYY'), 'M. Trabelsi, CAMO Airworthiness Reviewer (TN.CAMO.0088)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPB' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPB', 'ARC/TS-NPB/2025-02', ac.id, to_date('28 Feb 2026', 'DD Mon YYYY'), to_date('30 Mar 2026', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPB' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPD', 'CofA N° TN-AOC-2023-384', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPD' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPD', 'ARC/TS-NPD/2026-08', ac.id, to_date('10 Sep 2025', 'DD Mon YYYY'), to_date('10 Sep 2026', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPD' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPD', 'ARC/TS-NPD/2025-09', ac.id, to_date('11 Aug 2025', 'DD Mon YYYY'), to_date('10 Sep 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPD' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPE', 'CofA N° TN-AOC-2018-679', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPE' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPE', 'ARC/TS-NPE/2026-07', ac.id, to_date('22 Sep 2025', 'DD Mon YYYY'), to_date('22 Sep 2026', 'DD Mon YYYY'), 'M. Trabelsi, CAMO Airworthiness Reviewer (TN.CAMO.0088)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPE' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPE', 'ARC/TS-NPE/2025-04', ac.id, to_date('23 Aug 2025', 'DD Mon YYYY'), to_date('22 Sep 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPE' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPF', 'CofA N° TN-AOC-2023-492', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPF' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPF', 'ARC/TS-NPF/2026-07', ac.id, to_date('27 Dec 2025', 'DD Mon YYYY'), to_date('27 Dec 2026', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPF' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPF', 'ARC/TS-NPF/2025-07', ac.id, to_date('27 Nov 2025', 'DD Mon YYYY'), to_date('27 Dec 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPF' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPG', 'CofA N° TN-AOC-2022-436', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPG' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPG', 'ARC/TS-NPG/2026-01', ac.id, to_date('14 Mar 2026', 'DD Mon YYYY'), to_date('14 Mar 2027', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPG' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPG', 'ARC/TS-NPG/2025-07', ac.id, to_date('12 Feb 2026', 'DD Mon YYYY'), to_date('14 Mar 2026', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPG' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPH', 'CofA N° TN-AOC-2019-742', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPH' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPH', 'ARC/TS-NPH/2026-07', ac.id, to_date('17 Sep 2025', 'DD Mon YYYY'), to_date('17 Sep 2026', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPH' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPH', 'ARC/TS-NPH/2025-05', ac.id, to_date('18 Aug 2025', 'DD Mon YYYY'), to_date('17 Sep 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPH' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPI', 'CofA N° TN-AOC-2018-139', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPI' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPI', 'ARC/TS-NPI/2026-02', ac.id, to_date('05 Dec 2025', 'DD Mon YYYY'), to_date('05 Dec 2026', 'DD Mon YYYY'), 'M. Trabelsi, CAMO Airworthiness Reviewer (TN.CAMO.0088)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPI' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPI', 'ARC/TS-NPI/2025-05', ac.id, to_date('05 Nov 2025', 'DD Mon YYYY'), to_date('05 Dec 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPI' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPJ', 'CofA N° TN-AOC-2021-355', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPJ' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPJ', 'ARC/TS-NPJ/2026-07', ac.id, to_date('11 Dec 2025', 'DD Mon YYYY'), to_date('11 Dec 2026', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPJ' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPJ', 'ARC/TS-NPJ/2025-01', ac.id, to_date('11 Nov 2025', 'DD Mon YYYY'), to_date('11 Dec 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPJ' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPL', 'CofA N° TN-AOC-2020-426', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPL' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPL', 'ARC/TS-NPL/2026-12', ac.id, to_date('09 Nov 2025', 'DD Mon YYYY'), to_date('09 Nov 2026', 'DD Mon YYYY'), 'J. Haddad, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPL' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPL', 'ARC/TS-NPL/2025-02', ac.id, to_date('10 Oct 2025', 'DD Mon YYYY'), to_date('09 Nov 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPL' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPM', 'CofA N° TN-AOC-2022-112', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPM' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPM', 'ARC/TS-NPM/2026-07', ac.id, to_date('18 Sep 2025', 'DD Mon YYYY'), to_date('18 Sep 2026', 'DD Mon YYYY'), 'M. Trabelsi, CAMO Airworthiness Reviewer (TN.CAMO.0088)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPM' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPM', 'ARC/TS-NPM/2025-02', ac.id, to_date('19 Aug 2025', 'DD Mon YYYY'), to_date('18 Sep 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPM' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPN', 'CofA N° TN-AOC-2018-417', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPN' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPN', 'ARC/TS-NPN/2026-09', ac.id, to_date('21 Sep 2025', 'DD Mon YYYY'), to_date('21 Sep 2026', 'DD Mon YYYY'), 'M. Trabelsi, CAMO Airworthiness Reviewer (TN.CAMO.0088)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPN' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPN', 'ARC/TS-NPN/2025-02', ac.id, to_date('22 Aug 2025', 'DD Mon YYYY'), to_date('21 Sep 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPN' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPO', 'CofA N° TN-AOC-2020-775', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPO' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPO', 'ARC/TS-NPO/2026-03', ac.id, to_date('14 Apr 2026', 'DD Mon YYYY'), to_date('14 Apr 2027', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPO' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPO', 'ARC/TS-NPO/2025-08', ac.id, to_date('15 Mar 2026', 'DD Mon YYYY'), to_date('14 Apr 2026', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPO' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPP', 'CofA N° TN-AOC-2019-215', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPP' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPP', 'ARC/TS-NPP/2026-05', ac.id, to_date('12 Dec 2025', 'DD Mon YYYY'), to_date('12 Dec 2026', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPP' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPP', 'ARC/TS-NPP/2025-08', ac.id, to_date('12 Nov 2025', 'DD Mon YYYY'), to_date('12 Dec 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPP' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'Certificate of Airworthiness', 'Certificate of Airworthiness — TS-NPS', 'CofA N° TN-AOC-2023-239', ac.id, NULL, NULL, 'Civil aviation authority', 'CURRENT', NULL
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPS' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPS', 'ARC/TS-NPS/2026-01', ac.id, to_date('29 Dec 2025', 'DD Mon YYYY'), to_date('29 Dec 2026', 'DD Mon YYYY'), 'S. Ben Youssef, CAMO Airworthiness Reviewer (TN.CAMO.0042)', 'CURRENT', 'Full review — M.A.710(a)'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPS' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001'
UNION ALL
SELECT '00000000-0000-0000-0000-000000000001'::uuid, 'seed', 'annexe A4 - camoFleet', 'ARC', 'Airworthiness Review Certificate — TS-NPS', 'ARC/TS-NPS/2025-08', ac.id, to_date('29 Nov 2025', 'DD Mon YYYY'), to_date('29 Dec 2025', 'DD Mon YYYY'), 'CAMO Airworthiness Reviewer', 'SUPERSEDED', 'Full review'
  FROM camo.aircraft ac WHERE ac.registration = 'TS-NPS' AND ac.tenant_id = '00000000-0000-0000-0000-000000000001';
