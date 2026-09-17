-- ============================================================
--  V60 — La nature COMMERCIALE d'une etape
--
--  Le dossier de vol de l'annexe affiche deux choses tirees du
--  meme champ `flight.optype` : la pastille du heros
--  (« CHARTER · PAX », « POSITIONING / FERRY ») et la case
--  « Type of flight », qui porte la lettre de la case 8 du plan de
--  vol OACI (S / N / G / X). Chez elle, `optype` est un texte libre
--  attribue par un modulo dans son generateur.
--
--  Nous stockions deja la nature OPERATIONNELLE de l'etape
--  (`flight_type` : PAX, FERRY, POSITIONING, TRAINING, MAINTENANCE,
--  AMBULANCE) mais pas sa nature COMMERCIALE — programme, hors
--  programme, prive, vol d'Etat. Ce sont deux axes differents : un
--  vol passagers peut etre programme ou affrete, et c'est le second
--  axe qui decide de la lettre de la case 8. Faute de cette colonne,
--  la case affichait un tiret et la pastille se rabattait sur
--  « PAX ».
--
--  NON_SCHEDULED par defaut : c'est ce que depose un exploitant
--  d'affretement, et c'est la valeur que l'annexe attribue a quatre
--  vols sur cinq. Elle reste une valeur par defaut, pas une
--  deduction : le module Sales la posera a la creation du vol.
-- ============================================================
ALTER TABLE ops.legs
    ADD COLUMN commercial_type text NOT NULL DEFAULT 'NON_SCHEDULED';

ALTER TABLE ops.legs
    ADD CONSTRAINT ck_legs_commercial_type CHECK (commercial_type IN (
        'SCHEDULED', 'NON_SCHEDULED', 'PRIVATE', 'STATE'));

COMMENT ON COLUMN ops.legs.commercial_type IS
    'Commercial nature of the operation — drives ICAO FPL item 8 flight type (S/N/G/X)';
