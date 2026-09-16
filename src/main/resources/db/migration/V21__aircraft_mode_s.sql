-- ============================================================
--  V21 — le code Mode-S (ICAO 24 bits) de chaque appareil.
--
--  C'est la clé qui relie un appareil de la flotte à un message
--  ADS-B. Sans elle, aucune corrélation n'est possible.
--
--  Pourquoi la colonne reste NULL après cette migration
--  ----------------------------------------------------
--  Le prototype audité fabriquait ce code à partir d'un hachage de
--  l'immatriculation :
--
--      a.icao24 = '02' + hash;          // INDICATIVE demo placeholder
--      a.icao24Src = 'INDICATIVE-demo';
--
--  Il interrogeait donc un vrai flux OpenSky avec des identifiants
--  inventés — aucun de ses appareils ne pouvait correspondre, et
--  l'écran n'avait aucun moyen de le dire.
--
--  Ici la colonne est NULL tant que l'exploitant n'a pas saisi le
--  vrai code, qui figure sur le certificat d'immatriculation. NULL
--  n'est pas un manque à combler par une valeur plausible : c'est
--  l'information « on ne sait pas », et l'écran l'affiche telle
--  quelle. Un appareil sans code Mode-S ne sera jamais corrélé, et
--  Flight Watch le dira au lieu de laisser croire à une panne de
--  réception.
--
--  La contrainte impose six caractères hexadécimaux minuscules :
--  c'est la forme que renvoie OpenSky, et la comparer sans
--  normaliser serait une source de faux négatifs silencieux.
-- ============================================================

ALTER TABLE camo.aircraft
    ADD COLUMN mode_s_hex text,
    ADD COLUMN mode_s_source text;

ALTER TABLE camo.aircraft
    ADD CONSTRAINT ck_aircraft_mode_s
        CHECK (mode_s_hex IS NULL OR mode_s_hex ~ '^[0-9a-f]{6}$');

ALTER TABLE camo.aircraft
    ADD CONSTRAINT ck_aircraft_mode_s_source
        CHECK (mode_s_source IS NULL
               OR mode_s_source IN ('CERTIFICATE', 'OPERATOR', 'OBSERVED'));

COMMENT ON COLUMN camo.aircraft.mode_s_hex IS
    'Code Mode-S ICAO 24 bits, six caractères hexadécimaux minuscules. '
    'NULL tant qu''il n''a pas été saisi : jamais dérivé, jamais deviné.';

COMMENT ON COLUMN camo.aircraft.mode_s_source IS
    'D''où vient le code : CERTIFICATE (certificat d''immatriculation), '
    'OPERATOR (saisi par l''exploitant), OBSERVED (relevé sur un message reçu).';

CREATE UNIQUE INDEX uq_aircraft_mode_s
    ON camo.aircraft (tenant_id, mode_s_hex)
    WHERE mode_s_hex IS NOT NULL;
