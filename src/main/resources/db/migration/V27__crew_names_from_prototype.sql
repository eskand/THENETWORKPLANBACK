-- ============================================================
--  V27 — l'equipage seme prend les noms du prototype approuve.
--
--  POURQUOI. Le prototype (annexe A4) porte sa propre liste
--  d'equipage en dur : Ben Arbia Y., Trabelsi K., Khalifa M.…
--  L'operateur veut retrouver ces noms a l'ecran. Ils sont ici
--  repris a l'identique, dans l'ordre de la liste d'origine.
--
--  CE QUI NE BOUGE PAS. Aucune ligne n'est creee ni supprimee :
--  seuls first_name et last_name changent. Les identifiants, les
--  matricules, les licences, les visites medicales, les
--  qualifications, les vacations, les affectations et les
--  cellules de roster referencent des UUID — rien ne se detache.
--
--  LA CONVENTION DE NOM. Le prototype affiche « Ben Arbia Y. » :
--  le patronyme d'abord, l'initiale ensuite. Les colonnes gardent
--  leur sens — last_name porte le patronyme, first_name porte
--  l'initiale — et c'est Person.fullName() qui compose dans cet
--  ordre, comme le fait un plan d'equipage.
--
--  LE COMPTE. Le prototype a 36 CAP, 36 FO, 23 CC et 1 purser ;
--  la base en a 20, 20 et 10. On renomme les 50 existants avec
--  les 50 premiers noms de chaque rang. Creer les 46 manquants
--  demanderait de leur inventer licences, medicales et
--  qualifications : ils ne sont pas ajoutes.
-- ============================================================

UPDATE crew.persons SET first_name = 'Y.', last_name = 'Ben Arbia' WHERE staff_no = 'CPT001';
UPDATE crew.persons SET first_name = 'R.', last_name = 'Missaoui' WHERE staff_no = 'FO001';
UPDATE crew.persons SET first_name = 'K.', last_name = 'Trabelsi' WHERE staff_no = 'CPT002';
UPDATE crew.persons SET first_name = 'W.', last_name = 'Ayari' WHERE staff_no = 'FO002';
UPDATE crew.persons SET first_name = 'M.', last_name = 'Khalifa' WHERE staff_no = 'CPT003';
UPDATE crew.persons SET first_name = 'O.', last_name = 'Gharbi' WHERE staff_no = 'FO003';
UPDATE crew.persons SET first_name = 'H.', last_name = 'Cherni' WHERE staff_no = 'CPT004';
UPDATE crew.persons SET first_name = 'F.', last_name = 'Bouazizi' WHERE staff_no = 'FO004';
UPDATE crew.persons SET first_name = 'D.', last_name = 'Chouikha' WHERE staff_no = 'CPT005';
UPDATE crew.persons SET first_name = 'G.', last_name = 'Rezgui' WHERE staff_no = 'CPT006';
UPDATE crew.persons SET first_name = 'L.', last_name = 'Jaziri' WHERE staff_no = 'CPT007';
UPDATE crew.persons SET first_name = 'J.', last_name = 'Ayadi' WHERE staff_no = 'CPT008';
UPDATE crew.persons SET first_name = 'X.', last_name = 'Khemiri' WHERE staff_no = 'CPT009';
UPDATE crew.persons SET first_name = 'W.', last_name = 'Ghannouchi' WHERE staff_no = 'FO005';
UPDATE crew.persons SET first_name = 'E.', last_name = 'Chelbi' WHERE staff_no = 'FO006';
UPDATE crew.persons SET first_name = 'V.', last_name = 'Sassi' WHERE staff_no = 'FO007';
UPDATE crew.persons SET first_name = 'D.', last_name = 'Bouslimi' WHERE staff_no = 'FO008';
UPDATE crew.persons SET first_name = 'S.', last_name = 'Ghannouchi' WHERE staff_no = 'FO009';
UPDATE crew.persons SET first_name = 'H.', last_name = 'Bouzidi' WHERE staff_no = 'CPT010';
UPDATE crew.persons SET first_name = 'N.', last_name = 'Kefi' WHERE staff_no = 'CPT011';
UPDATE crew.persons SET first_name = 'R.', last_name = 'Sfaxi' WHERE staff_no = 'CPT012';
UPDATE crew.persons SET first_name = 'K.', last_name = 'Dhaoui' WHERE staff_no = 'CPT013';
UPDATE crew.persons SET first_name = 'B.', last_name = 'Naceur' WHERE staff_no = 'CPT014';
UPDATE crew.persons SET first_name = 'F.', last_name = 'Werghi' WHERE staff_no = 'CPT015';
UPDATE crew.persons SET first_name = 'A.', last_name = 'Mejri' WHERE staff_no = 'FO010';
UPDATE crew.persons SET first_name = 'L.', last_name = 'Chaari' WHERE staff_no = 'FO011';
UPDATE crew.persons SET first_name = 'S.', last_name = 'Barhoumi' WHERE staff_no = 'FO012';
UPDATE crew.persons SET first_name = 'Y.', last_name = 'Aloui' WHERE staff_no = 'FO013';
UPDATE crew.persons SET first_name = 'O.', last_name = 'Ferjeni' WHERE staff_no = 'FO014';
UPDATE crew.persons SET first_name = 'T.', last_name = 'Bahri' WHERE staff_no = 'FO015';
UPDATE crew.persons SET first_name = 'G.', last_name = 'Cherif' WHERE staff_no = 'CPT016';
UPDATE crew.persons SET first_name = 'R.', last_name = 'Toumi' WHERE staff_no = 'CPT017';
UPDATE crew.persons SET first_name = 'S.', last_name = 'Souissi' WHERE staff_no = 'CPT018';
UPDATE crew.persons SET first_name = 'O.', last_name = 'Bouhlel' WHERE staff_no = 'CPT019';
UPDATE crew.persons SET first_name = 'T.', last_name = 'Bouhlel' WHERE staff_no = 'CPT020';
UPDATE crew.persons SET first_name = 'I.', last_name = 'Boughanmi' WHERE staff_no = 'FO016';
UPDATE crew.persons SET first_name = 'N.', last_name = 'Hamdi' WHERE staff_no = 'FO017';
UPDATE crew.persons SET first_name = 'V.', last_name = 'Amara' WHERE staff_no = 'FO018';
UPDATE crew.persons SET first_name = 'D.', last_name = 'Slama' WHERE staff_no = 'FO019';
UPDATE crew.persons SET first_name = 'L.', last_name = 'Selmi' WHERE staff_no = 'FO020';
UPDATE crew.persons SET first_name = 'S.', last_name = 'Hammami' WHERE staff_no = 'CC001';
UPDATE crew.persons SET first_name = 'L.', last_name = 'Ben Amor' WHERE staff_no = 'CC002';
UPDATE crew.persons SET first_name = 'N.', last_name = 'Saada' WHERE staff_no = 'CC003';
UPDATE crew.persons SET first_name = 'D.', last_name = 'Amri' WHERE staff_no = 'CC004';
UPDATE crew.persons SET first_name = 'U.', last_name = 'Bouazza' WHERE staff_no = 'CC005';
UPDATE crew.persons SET first_name = 'C.', last_name = 'Sassi' WHERE staff_no = 'CC006';
UPDATE crew.persons SET first_name = 'S.', last_name = 'Ammar' WHERE staff_no = 'CC007';
UPDATE crew.persons SET first_name = 'U.', last_name = 'Ferjani' WHERE staff_no = 'CC008';
UPDATE crew.persons SET first_name = 'H.', last_name = 'Sioud' WHERE staff_no = 'CC009';
UPDATE crew.persons SET first_name = 'M.', last_name = 'Souissi' WHERE staff_no = 'CC010';
