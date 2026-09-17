-- ============================================================
--  V55 — l'analyse d'enquete telle que l'annexe A4 la montre.
--
--  CE QUI MANQUAIT. Une enquete portait sa cause racine en une
--  phrase et ses facteurs contributifs en une chaine de caracteres
--  separee par des points mediands. Le prototype, lui, affiche la
--  CHAINE des cinq pourquoi — cinq etapes numerotees, du fait
--  constate jusqu'a la condition organisationnelle qui l'a permis —
--  puis les recommandations de securite qui en decoulent, puis un
--  pourcentage d'avancement.
--
--  POURQUOI DES TABLES ET PAS DU TEXTE. La chaine est ordonnee et
--  chaque maillon se lit seul : c'est une liste, pas un paragraphe.
--  La garder dans une colonne texte obligerait l'ecran a la
--  redecouper a chaque rendu, et la premiere etape contenant un
--  point median casserait le decoupage sans que personne ne le voie.
--  ICAO Doc 9859 demande que l'analyse soit tracable maillon par
--  maillon ; une colonne texte ne l'est pas.
--
--  L'AVANCEMENT EST STOCKE, PAS DERIVE. On pourrait le calculer
--  depuis l'ecart entre l'ouverture et l'echeance, mais ce serait le
--  temps ecoule, pas le travail fait : une enquete ouverte depuis
--  trois semaines sur quatre n'est pas a 75 %. C'est l'enqueteur qui
--  le declare.
-- ============================================================

ALTER TABLE safety.investigations
    ADD COLUMN progress_percent smallint NOT NULL DEFAULT 0
        CONSTRAINT ck_investigation_progress CHECK (progress_percent BETWEEN 0 AND 100);

-- ── la chaine des cinq pourquoi ──────────────────────────────
--  `position` porte l'ordre : le maillon 1 est le fait constate, le
--  dernier la condition organisationnelle. L'ecran colore le dernier
--  differemment parce que c'est celui sur lequel on agit.
CREATE TABLE safety.investigation_steps (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES platform.tenants(id),
    investigation_id uuid NOT NULL REFERENCES safety.investigations(id) ON DELETE CASCADE,
    position         smallint NOT NULL,
    statement        text NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investigation_step UNIQUE (investigation_id, position)
);

CREATE INDEX ix_investigation_steps_inv ON safety.investigation_steps (investigation_id, position);

-- ── les recommandations de securite ──────────────────────────
CREATE TABLE safety.investigation_recommendations (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid NOT NULL REFERENCES platform.tenants(id),
    investigation_id uuid NOT NULL REFERENCES safety.investigations(id) ON DELETE CASCADE,
    position         smallint NOT NULL,
    recommendation   text NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_investigation_recommendation UNIQUE (investigation_id, position)
);

CREATE INDEX ix_investigation_recos_inv
    ON safety.investigation_recommendations (investigation_id, position);

-- ── les deux enquetes de l'annexe, avec leur chaine ──────────
UPDATE safety.investigations SET progress_percent = 20 WHERE reference = 'INV-2026-0003';
UPDATE safety.investigations SET progress_percent = 70 WHERE reference = 'INV-2026-0001';

INSERT INTO safety.investigation_steps (tenant_id, investigation_id, position, statement)
SELECT i.tenant_id, i.id, s.position, s.statement
  FROM safety.investigations i
  JOIN (VALUES
    ('INV-2026-0003', 1, 'Left engine nacelle struck a bird on final approach RWY 27R'),
    ('INV-2026-0003', 2, 'Bird activity was present in the approach corridor at first light'),
    ('INV-2026-0003', 3, 'No wildlife activity advisory had been issued for that period'),
    ('INV-2026-0003', 4, 'Wildlife activity is not part of the standard pre-departure briefing for this aerodrome'),
    ('INV-2026-0003', 5, 'The operator has no seasonal wildlife risk assessment for high-activity fields'),
    ('INV-2026-0001', 1, 'A temporary restricted area was not shown on the operational flight plan'),
    ('INV-2026-0001', 2, 'The NOTAM was published after the OFP had been generated'),
    ('INV-2026-0001', 3, 'No re-check of NOTAMs was performed between OFP generation and crew briefing'),
    ('INV-2026-0001', 4, 'The flight planning checklist does not require a late NOTAM re-check'),
    ('INV-2026-0001', 5, 'The checklist was last revised before the current flight planning system was introduced')
  ) AS s(reference, position, statement) ON s.reference = i.reference
ON CONFLICT (investigation_id, position) DO NOTHING;

INSERT INTO safety.investigation_recommendations (tenant_id, investigation_id, position, recommendation)
SELECT i.tenant_id, i.id, r.position, r.recommendation
  FROM safety.investigations i
  JOIN (VALUES
    ('INV-2026-0003', 1, 'Add a wildlife activity item to the pre-departure aerodrome briefing.'),
    ('INV-2026-0003', 2, 'Produce a seasonal wildlife risk assessment for the five highest-activity aerodromes.'),
    ('INV-2026-0001', 1, 'Add a mandatory NOTAM re-check within 60 minutes of the crew briefing.'),
    ('INV-2026-0001', 2, 'Revise the flight planning checklist and re-issue it to all dispatchers.')
  ) AS r(reference, position, recommendation) ON r.reference = i.reference
ON CONFLICT (investigation_id, position) DO NOTHING;
