-- ============================================================
--  V49 — rattacher chaque report a la BONNE ligne du MEL.
--
--  LE DEFAUT. V11 rattachait chaque report existant a la premiere
--  ligne de bibliotheque de la meme CATEGORIE, pour le meme type
--  d'aeronef. Son commentaire annonce « quand le chapitre ATA
--  correspond » ; le SQL ne regarde jamais le chapitre.
--
--  Resultat : « MEL 34-51-07 — recepteur DME n°2 » (ATA 34) pointait
--  sur « Cabin pressure controller » (ATA 21). Tant que l'ecran
--  n'affichait que la categorie et l'echeance, cela ne se voyait pas.
--  Depuis que la Hold Item List affiche le chapitre ATA et le systeme
--  — comme l'annexe A4 — cela se lit sur chaque ligne.
--
--  LA CORRECTION. On relie par le chapitre ATA, lu dans la reference
--  du report lui-meme : « MEL 34-51-07 » est un report ATA 34. Et
--  quand aucune ligne ne correspond, on DETACHE plutot que de garder
--  un mauvais lien. Un report sans ligne de bibliotheque se voit et
--  se corrige ; un report rattache a la mauvaise ligne affiche un
--  systeme faux avec l'aplomb d'une donnee juste.
-- ============================================================

-- Detacher d'abord : un lien pose sur la seule categorie ne vaut rien.
UPDATE camo.mel_items SET mel_library_id = NULL
 WHERE mel_library_id IS NOT NULL
   AND source_type = 'seed';

-- Relier par chapitre ATA, pour le type de l'appareil concerne.
UPDATE camo.mel_items mi
   SET mel_library_id = lib.id
  FROM camo.aircraft a
  JOIN camo.mel_library lib ON lib.aircraft_type_id = a.aircraft_type_id
 WHERE mi.aircraft_id = a.id
   AND mi.mel_library_id IS NULL
   -- « MEL 34-51-07 » → « 34 ». La reference porte le chapitre.
   AND lib.ata_chapter = substring(mi.reference from '(\d{2})-')
   AND lib.id = (
       SELECT l2.id FROM camo.mel_library l2
        WHERE l2.aircraft_type_id = a.aircraft_type_id
          AND l2.ata_chapter = substring(mi.reference from '(\d{2})-')
        ORDER BY l2.item_ref
        LIMIT 1);

-- ------------------------------------------------------------
--  Le chapitre ATA des pannes
-- ------------------------------------------------------------
--  Six pannes sur huit portaient « 00 » : un chapitre qui n'existe
--  pas dans l'ATA 100. Le Tech Log affiche desormais le nom du
--  systeme en face du numero, et « ATA 00 — » ne nomme rien.
--
--  Quand la panne a ete reportee sous une ligne MEL, le chapitre est
--  celui de cette ligne. Sinon on laisse NULL : l'ecran affiche alors
--  un tiret, ce qui est vrai, plutot qu'un « 00 » qui ne l'est pas.
UPDATE camo.defects d
   SET ata_chapter = substring(m.reference from '(\d{2})-')
  FROM camo.mel_items m
 WHERE d.mel_item_id = m.id
   AND (d.ata_chapter IS NULL OR d.ata_chapter IN ('00', ''));

UPDATE camo.defects
   SET ata_chapter = NULL
 WHERE ata_chapter IN ('00', '');
