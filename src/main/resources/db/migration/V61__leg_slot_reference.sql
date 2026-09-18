-- ============================================================
--  V61 — La reference du creneau ATC a cote du CTOT
--
--  `ops.legs.ctot` porte deja l'heure de decollage calculee
--  (Calculated Take-Off Time). L'annexe en stocke deux choses,
--  pas une : `flight._occ.slot = { ctot, ref }` — l'heure ET la
--  reference du message de regulation (CFMU / EUROCONTROL, ou le
--  numero donne par la tour).
--
--  La reference n'est pas decorative. Un CTOT sans reference n'est
--  pas verifiable : c'est elle qu'un controleur cite pour revoir un
--  creneau, et c'est elle qui distingue un creneau recu d'un
--  creneau estime par le dispatcher. La frise OCC l'affiche sous
--  l'evenement « ATC Slot », exactement comme l'annexe.
-- ============================================================
ALTER TABLE ops.legs
    ADD COLUMN ctot_ref text;

COMMENT ON COLUMN ops.legs.ctot_ref IS
    'ATC slot reference the CTOT was issued under (CFMU / EUROCONTROL message ref)';
