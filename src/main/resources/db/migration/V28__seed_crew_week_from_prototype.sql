-- ============================================================
--  V28 — la semaine d'equipage du prototype, posee sur la
--  semaine courante.
--
--  POURQUOI. Les ecrans Crew Scheduling et Roster montraient une
--  semaine vide. Ce n'etait pas un defaut d'affichage : les seeds
--  V8/V10/V14 datent leurs lignes avec now() AU MOMENT DE LA
--  MIGRATION, si bien que la journee d'exploitation reste figee au
--  jour ou Flyway a tourne. Quelques jours plus tard, la semaine
--  demandee ne contient plus rien. Le prototype (annexe A4) n'a
--  pas ce probleme parce qu'il ne lit aucune base : sa grille
--  rejoue un motif hebdomadaire indexe sur le jour calendaire
--  (csDutyCellHtml, l. 20887), donc toute semaine est pleine.
--
--  CE QUE FAIT CETTE MIGRATION. Elle reprend ce motif — le tableau
--  crewData du prototype, l. 19938-20403 — et le pose sur la
--  semaine courante : sept journees par equipage, les vols en
--  etapes reelles avec leurs sieges, tout le reste en cellules de
--  roster. Les ecrans montrent alors ce que montre le prototype,
--  sur des lignes que l'on peut ouvrir, modifier et affecter.
--
--  LA ROTATION EST CELLE DU PROTOTYPE. Il choisit la vacation du
--  jour par floor(t/86400000) % duties.length : la vacation d'une
--  date donnee est donc la meme ici que la-bas. Un equipage qui
--  n'a que six vacations tourne sur six et non sur sept — la
--  colonne cycle porte cette longueur.
--
--  QUI EST SERVI. Les 50 equipages de la base, retrouves par leur
--  nom : V27 leur a donne les noms du prototype, et la jointure se
--  fait dessus. Les 51 autres personnes du prototype n'existent pas
--  en base ; leur creer licences, medicales et qualifications
--  reviendrait a les inventer, et V27 avait deja refuse de le faire.
--
--  LE VERDICT FTL EST SEME, PAS CALCULE. Les sieges portent 'OK'
--  comme les affectations de V8, et comme le prototype qui affiche
--  « 0 illegal · 0 warning ». Le moteur ORO.FTL qui produira de
--  vrais verdicts est le sprint S7 ; d'ici la, ces 'OK' disent
--  « rien n'a ete verifie », pas « c'est legal ».
--
--  RELANCER. Cette migration pose la semaine du jour ou elle
--  tourne. Pour ramener le motif sur la semaine courante plus tard,
--  db/tools/refresh_crew_week.sql rejoue le meme travail, autant de
--  fois qu'on veut.
-- ============================================================

CREATE TEMP TABLE proto_pattern (
    crew_name  text    NOT NULL,
    cycle      int     NOT NULL,
    slot       int     NOT NULL,
    code       text    NOT NULL,
    flight_no  text,
    dep_icao   text,
    arr_icao   text,
    dep_min    int,
    arr_min    int,
    arr_next   boolean NOT NULL,
    fleet      text,
    seat       text
) ON COMMIT DROP;

INSERT INTO proto_pattern
    (crew_name, cycle, slot, code, flight_no, dep_icao, arr_icao, dep_min, arr_min, arr_next, fleet, seat)
VALUES
  ('Ben Arbia Y.', 7, 0, 'FLT', 'TNP101', 'DTTA', 'LFPG', 360, 555, false, 'FALCON', 'CPT'),
  ('Ben Arbia Y.', 7, 1, 'FLT', 'TNP215', 'LFPG', 'EDDF', 630, 735, false, 'FALCON', 'CPT'),
  ('Ben Arbia Y.', 7, 2, 'FLT', 'TNP309', 'EDDF', 'OMDB', 1005, 130, true, 'FALCON', 'CPT'),
  ('Ben Arbia Y.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ben Arbia Y.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ben Arbia Y.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ben Arbia Y.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Missaoui R.', 7, 0, 'FLT', 'TNP101', 'DTTA', 'LFPG', 360, 555, false, 'FALCON', 'FO'),
  ('Missaoui R.', 7, 1, 'FLT', 'TNP215', 'LFPG', 'EDDF', 630, 735, false, 'FALCON', 'FO'),
  ('Missaoui R.', 7, 2, 'FLT', 'TNP309', 'EDDF', 'OMDB', 1005, 130, true, 'FALCON', 'FO'),
  ('Missaoui R.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Missaoui R.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Missaoui R.', 7, 5, 'TRG', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Missaoui R.', 7, 6, 'TRG', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Trabelsi K.', 7, 0, 'FLT', 'TNP103', 'DTTA', 'LFPO', 330, 525, false, 'CITATION', 'CPT'),
  ('Trabelsi K.', 7, 1, 'FLT', 'TNP217', 'LFPO', 'EBBR', 585, 675, false, 'CITATION', 'CPT'),
  ('Trabelsi K.', 7, 2, 'FLT', 'TNP218', 'EBBR', 'DTTA', 750, 915, false, 'CITATION', 'CPT'),
  ('Trabelsi K.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Trabelsi K.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Trabelsi K.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Trabelsi K.', 7, 6, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Ayari W.', 7, 0, 'FLT', 'TNP103', 'DTTA', 'LFPO', 330, 525, false, 'CITATION', 'FO'),
  ('Ayari W.', 7, 1, 'FLT', 'TNP217', 'LFPO', 'EBBR', 585, 675, false, 'CITATION', 'FO'),
  ('Ayari W.', 7, 2, 'FLT', 'TNP218', 'EBBR', 'DTTA', 750, 915, false, 'CITATION', 'FO'),
  ('Ayari W.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Ayari W.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Ayari W.', 7, 5, 'SBY', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Ayari W.', 7, 6, 'SBY', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Khalifa M.', 7, 0, 'FLT', 'TNP418', 'DTTA', 'LTFM', 420, 645, false, 'LEGACY', 'CPT'),
  ('Khalifa M.', 7, 1, 'FLT', 'TNP501', 'LTFM', 'LIRF', 750, 840, false, 'LEGACY', 'CPT'),
  ('Khalifa M.', 7, 2, 'FLT', 'TNP502', 'LIRF', 'DTTA', 930, 1065, false, 'LEGACY', 'CPT'),
  ('Khalifa M.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'CPT'),
  ('Khalifa M.', 7, 4, 'SBY', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'CPT'),
  ('Khalifa M.', 7, 5, 'POS', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'CPT'),
  ('Khalifa M.', 7, 6, 'POS', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'CPT'),
  ('Gharbi O.', 7, 0, 'FLT', 'TNP418', 'DTTA', 'LTFM', 420, 645, false, 'LEGACY', 'FO'),
  ('Gharbi O.', 7, 1, 'FLT', 'TNP501', 'LTFM', 'LIRF', 750, 840, false, 'LEGACY', 'FO'),
  ('Gharbi O.', 7, 2, 'FLT', 'TNP502', 'LIRF', 'DTTA', 930, 1065, false, 'LEGACY', 'FO'),
  ('Gharbi O.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'FO'),
  ('Gharbi O.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'FO'),
  ('Gharbi O.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'FO'),
  ('Gharbi O.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'LEGACY', 'FO'),
  ('Cherni H.', 7, 0, 'FLT', 'TNP810', 'DTTA', 'KJFK', 60, 570, false, 'LINEAGE', 'CPT'),
  ('Cherni H.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'CPT'),
  ('Cherni H.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'CPT'),
  ('Cherni H.', 7, 3, 'FLT', 'TNP811', 'KJFK', 'DTTA', 1110, 0, true, 'LINEAGE', 'CPT'),
  ('Cherni H.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'CPT'),
  ('Cherni H.', 7, 5, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'CPT'),
  ('Cherni H.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'CPT'),
  ('Bouazizi F.', 7, 0, 'FLT', 'TNP810', 'DTTA', 'KJFK', 60, 570, false, 'LINEAGE', 'FO'),
  ('Bouazizi F.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'FO'),
  ('Bouazizi F.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'FO'),
  ('Bouazizi F.', 7, 3, 'FLT', 'TNP811', 'KJFK', 'DTTA', 1110, 0, true, 'LINEAGE', 'FO'),
  ('Bouazizi F.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'FO'),
  ('Bouazizi F.', 7, 5, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'FO'),
  ('Bouazizi F.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'LINEAGE', 'FO'),
  ('Chouikha D.', 7, 0, 'FLT', 'TNP211', 'LFPG', 'EDDF', 570, 675, false, 'FALCON', 'CPT'),
  ('Chouikha D.', 7, 1, 'FLT', 'TNP210', 'LFPG', 'EDDF', 570, 675, false, 'FALCON', 'CPT'),
  ('Chouikha D.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Chouikha D.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Chouikha D.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Chouikha D.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Chouikha D.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 0, 'FLT', 'TNP210', 'LFPO', 'EBBR', 360, 525, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 1, 'FLT', 'TNP301', 'EDDF', 'OMDB', 780, 930, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Rezgui G.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 0, 'FLT', 'TNP211', 'EBBR', 'DTTA', 570, 675, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 1, 'FLT', 'TNP104', 'DTTA', 'LFPO', 840, 980, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Jaziri L.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 0, 'FLT', 'TNP105', 'EDDF', 'OMDB', 630, 765, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 1, 'FLT', 'TNP213', 'DTTA', 'LFPO', 780, 930, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ayadi J.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 0, 'FLT', 'TNP301', 'EDDF', 'OMDB', 975, 1080, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 1, 'FLT', 'TNP213', 'EBBR', 'DTTA', 630, 765, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Khemiri X.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Ghannouchi W.', 7, 0, 'FLT', 'TNP301', 'EBBR', 'DTTA', 780, 930, false, 'FALCON', 'FO'),
  ('Ghannouchi W.', 7, 1, 'FLT', 'TNP213', 'DTTA', 'LFPO', 435, 585, false, 'FALCON', 'FO'),
  ('Ghannouchi W.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi W.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi W.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi W.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi W.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 0, 'FLT', 'TNP105', 'DTTA', 'LFPG', 780, 930, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 1, 'FLT', 'TNP104', 'LFPG', 'EDDF', 840, 980, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chelbi E.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 0, 'FLT', 'TNP302', 'EBBR', 'DTTA', 570, 675, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 1, 'FLT', 'TNP211', 'EDDF', 'OMDB', 435, 585, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Sassi V.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 0, 'FLT', 'TNP302', 'LFPG', 'EDDF', 780, 930, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 1, 'FLT', 'TNP210', 'EDDF', 'OMDB', 360, 525, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bouslimi D.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 0, 'FLT', 'TNP301', 'LFPO', 'EBBR', 780, 930, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 1, 'FLT', 'TNP105', 'LFPG', 'EDDF', 345, 490, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ghannouchi S.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bouzidi H.', 6, 0, 'FLT', 'TNP104', 'DTTA', 'LFPO', 435, 585, false, 'FALCON', 'CPT'),
  ('Bouzidi H.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Bouzidi H.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Bouzidi H.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Bouzidi H.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Bouzidi H.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Kefi N.', 6, 0, 'FLT', 'TNP211', 'DTTA', 'EDDF', 570, 720, false, 'FALCON', 'CPT'),
  ('Kefi N.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Kefi N.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Kefi N.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Kefi N.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Kefi N.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Sfaxi R.', 6, 0, 'FLT', 'TNP302', 'DTTA', 'LEBL', 780, 930, false, 'FALCON', 'CPT'),
  ('Sfaxi R.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Sfaxi R.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Sfaxi R.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Sfaxi R.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Sfaxi R.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Dhaoui K.', 6, 0, 'FLT', 'TNP210', 'DTTA', 'OMDB', 360, 525, false, 'FALCON', 'CPT'),
  ('Dhaoui K.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Dhaoui K.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Dhaoui K.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Dhaoui K.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Dhaoui K.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Naceur B.', 6, 0, 'FLT', 'TNP213', 'DTTA', 'LFPO', 840, 980, false, 'FALCON', 'CPT'),
  ('Naceur B.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Naceur B.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Naceur B.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Naceur B.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Naceur B.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Werghi F.', 6, 0, 'FLT', 'TNP301', 'DTTA', 'LFPO', 975, 1080, false, 'FALCON', 'CPT'),
  ('Werghi F.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Werghi F.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Werghi F.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Werghi F.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Werghi F.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'CPT'),
  ('Mejri A.', 6, 0, 'FLT', 'TNP105', 'DTTA', 'LFPG', 780, 930, false, 'FALCON', 'FO'),
  ('Mejri A.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Mejri A.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Mejri A.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Mejri A.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Mejri A.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chaari L.', 6, 0, 'FLT', 'TNP210', 'DTTA', 'OMDB', 360, 525, false, 'FALCON', 'FO'),
  ('Chaari L.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chaari L.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chaari L.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chaari L.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Chaari L.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Barhoumi S.', 6, 0, 'FLT', 'TNP211', 'DTTA', 'EDDF', 570, 720, false, 'FALCON', 'FO'),
  ('Barhoumi S.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Barhoumi S.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Barhoumi S.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Barhoumi S.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Barhoumi S.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Aloui Y.', 6, 0, 'FLT', 'TNP302', 'DTTA', 'LEBL', 780, 930, false, 'FALCON', 'FO'),
  ('Aloui Y.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Aloui Y.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Aloui Y.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Aloui Y.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Aloui Y.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ferjeni O.', 6, 0, 'FLT', 'TNP301', 'DTTA', 'LFPO', 975, 1080, false, 'FALCON', 'FO'),
  ('Ferjeni O.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ferjeni O.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ferjeni O.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ferjeni O.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Ferjeni O.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bahri T.', 6, 0, 'FLT', 'TNP213', 'DTTA', 'LFPO', 840, 980, false, 'FALCON', 'FO'),
  ('Bahri T.', 6, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bahri T.', 6, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bahri T.', 6, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bahri T.', 6, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Bahri T.', 6, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'FALCON', 'FO'),
  ('Cherif G.', 7, 0, 'FLT', 'TNP406', 'DTTA', 'LMML', 435, 585, false, 'CITATION', 'CPT'),
  ('Cherif G.', 7, 1, 'FLT', 'TNP407', 'LFMN', 'DTTA', 345, 490, false, 'CITATION', 'CPT'),
  ('Cherif G.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Cherif G.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Cherif G.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Cherif G.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Cherif G.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 0, 'FLT', 'TNP411', 'DTTA', 'LMML', 780, 930, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 1, 'FLT', 'TNP401', 'DTTA', 'DAAG', 360, 525, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Toumi R.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 0, 'FLT', 'TNP402', 'LMML', 'DTTA', 630, 765, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 1, 'FLT', 'TNP410', 'LFMN', 'DTTA', 435, 585, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Souissi S.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 0, 'FLT', 'TNP401', 'LMML', 'DTTA', 975, 1080, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 1, 'FLT', 'TNP408', 'LMML', 'DTTA', 435, 585, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel O.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 0, 'FLT', 'TNP410', 'LMML', 'DTTA', 435, 585, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 1, 'FLT', 'TNP415', 'DTTA', 'DTTJ', 570, 675, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Bouhlel T.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'CPT'),
  ('Boughanmi I.', 7, 0, 'FLT', 'TNP409', 'DTTA', 'LMML', 840, 980, false, 'CITATION', 'FO'),
  ('Boughanmi I.', 7, 1, 'FLT', 'TNP415', 'LMML', 'DTTA', 435, 585, false, 'CITATION', 'FO'),
  ('Boughanmi I.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Boughanmi I.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Boughanmi I.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Boughanmi I.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Boughanmi I.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 0, 'FLT', 'TNP404', 'DTTA', 'DTTJ', 630, 765, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 1, 'FLT', 'TNP402', 'DTTA', 'LMML', 780, 930, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Hamdi N.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 0, 'FLT', 'TNP414', 'DTTA', 'DTTJ', 780, 930, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 1, 'FLT', 'TNP410', 'DTTA', 'LEPA', 780, 930, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Amara V.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 0, 'FLT', 'TNP416', 'DTTA', 'LEPA', 630, 765, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 1, 'FLT', 'TNP404', 'DTTA', 'DAAG', 435, 585, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Slama D.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 0, 'FLT', 'TNP411', 'LMML', 'DTTA', 780, 930, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 1, 'FLT', 'TNP401', 'LEPA', 'DTTA', 435, 585, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Selmi L.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, 'CITATION', 'FO'),
  ('Hammami S.', 7, 0, 'FLT', 'TNP101', 'DTTA', 'LFPG', 360, 555, false, NULL, 'CABIN_1'),
  ('Hammami S.', 7, 1, 'FLT', 'TNP215', 'LFPG', 'EDDF', 630, 735, false, NULL, 'CABIN_1'),
  ('Hammami S.', 7, 2, 'FLT', 'TNP309', 'EDDF', 'OMDB', 1005, 130, true, NULL, 'CABIN_1'),
  ('Hammami S.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Hammami S.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Hammami S.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Hammami S.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 0, 'FLT', 'TNP101', 'DTTA', 'LFPG', 360, 555, false, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 1, 'FLT', 'TNP215', 'LFPG', 'EDDF', 630, 735, false, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 2, 'FLT', 'TNP309', 'EDDF', 'OMDB', 1005, 130, true, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ben Amor L.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 0, 'FLT', 'TNP418', 'DTTA', 'LTFM', 420, 645, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 1, 'FLT', 'TNP501', 'LTFM', 'LIRF', 750, 840, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 2, 'FLT', 'TNP502', 'LIRF', 'DTTA', 930, 1065, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 3, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 4, 'LVE', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 5, 'LVE', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Saada N.', 7, 6, 'LVE', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Amri D.', 7, 0, 'FLT', 'TNP810', 'DTTA', 'KJFK', 60, 570, false, NULL, 'CABIN_1'),
  ('Amri D.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Amri D.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Amri D.', 7, 3, 'FLT', 'TNP811', 'KJFK', 'DTTA', 1110, 0, true, NULL, 'CABIN_1'),
  ('Amri D.', 7, 4, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Amri D.', 7, 5, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Amri D.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 0, 'FLT', 'TNP302', 'DTTA', 'LFPO', 570, 675, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Bouazza U.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 0, 'FLT', 'TNP105', 'DTTA', 'LFPG', 630, 765, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sassi C.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 0, 'FLT', 'TNP301', 'DTTA', 'LFPO', 975, 1080, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ammar S.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 0, 'FLT', 'TNP213', 'EBBR', 'DTTA', 630, 765, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Ferjani U.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 0, 'FLT', 'TNP302', 'DTTA', 'LFPG', 360, 525, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Sioud H.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 0, 'FLT', 'TNP402', 'LFMN', 'DTTA', 360, 525, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 1, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 2, 'OFF', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 3, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 4, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 5, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1'),
  ('Souissi M.', 7, 6, 'RES', NULL, NULL, NULL, NULL, NULL, false, NULL, 'CABIN_1');

-- La semaine visee : lundi a dimanche, celle qui contient aujourd'hui.
-- date_trunc('week') tombe sur le lundi en PostgreSQL, comme la grille.
CREATE TEMP TABLE proto_week ON COMMIT DROP AS
SELECT day::date AS day,
       (day::date - DATE '1970-01-01') AS epoch_day
FROM generate_series(
         date_trunc('week', now() AT TIME ZONE 'UTC')::date,
         date_trunc('week', now() AT TIME ZONE 'UTC')::date + 6,
         interval '1 day') AS day;

-- Une ligne par equipage et par journee : la vacation que le prototype
-- afficherait a cette date, pour les seules personnes qui existent en base.
CREATE TEMP TABLE proto_day ON COMMIT DROP AS
SELECT p.id AS person_id,
       w.day,
       pp.code,
       pp.flight_no,
       pp.dep_icao,
       pp.arr_icao,
       pp.dep_min,
       pp.arr_min,
       pp.arr_next,
       pp.fleet,
       pp.seat
FROM proto_pattern pp
JOIN crew.persons p
       ON p.last_name || ' ' || p.first_name = pp.crew_name
      AND p.tenant_id = '00000000-0000-0000-0000-000000000001'
      AND p.active
CROSS JOIN proto_week w
WHERE pp.slot = (w.epoch_day % pp.cycle);

-- ------------------------------------------------------------
--  Les etapes
--
--  Une etape par (journee, numero de vol) : un vol porte un
--  commandant et un copilote, et c'est le meme vol. business_key
--  contient la date, donc la semaine prochaine ne heurtera pas
--  celle-ci.
--
--  Le tail est choisi dans la bonne famille quand elle existe, et
--  reparti entre les avions de cette famille par un hachage du
--  numero de vol — sinon toute la flotte Falcon volerait sur le
--  meme appareil. Faute de famille, n'importe quel appareil du
--  tenant fait l'affaire : aircraft_id est NOT NULL, et une etape
--  sans avion ne serait pas une etape.
-- ------------------------------------------------------------
INSERT INTO ops.legs (id, tenant_id, aircraft_id, flight_no, dep_icao, arr_icao, base_icao,
                      std, sta, status, flight_type, pax_count,
                      source_type, source_ref, business_key)
SELECT md5('proto-leg-' || f.day || '-' || f.flight_no)::uuid,
       '00000000-0000-0000-0000-000000000001',
       a.id,
       f.flight_no,
       f.dep_icao,
       f.arr_icao,
       'DTTA',
       ((f.day + make_interval(mins => f.dep_min)) AT TIME ZONE 'UTC'),
       ((f.day + make_interval(days => CASE WHEN f.arr_next THEN 1 ELSE 0 END, mins => f.arr_min))
            AT TIME ZONE 'UTC'),
       CASE WHEN f.day < CURRENT_DATE THEN 'ARRIVED' ELSE 'PLANNED' END,
       'PAX',
       0,
       'seed',
       'NetPlus RFP annexe A4 — prototype iteration 33',
       'PROTO-' || f.flight_no || '-' || to_char(f.day, 'YYYYMMDD')
FROM (
    SELECT DISTINCT ON (day, flight_no)
           day, flight_no, dep_icao, arr_icao, dep_min, arr_min, arr_next, fleet
    FROM proto_day
    WHERE code = 'FLT' AND flight_no IS NOT NULL
    ORDER BY day, flight_no, dep_min
) f
JOIN LATERAL (
    SELECT ac.id
    FROM camo.aircraft ac
    JOIN refdata.aircraft_types t ON t.id = ac.aircraft_type_id
    WHERE ac.tenant_id = '00000000-0000-0000-0000-000000000001'
    ORDER BY (upper(split_part(t.model, ' ', 1)) = f.fleet) DESC,
             md5(f.flight_no || ac.registration)
    LIMIT 1
) a ON true
ON CONFLICT (business_key) DO NOTHING;

-- ------------------------------------------------------------
--  Les sieges
--
--  DISTINCT ON (etape, siege) : deux commandants sur le meme vol le
--  meme jour ne peuvent pas occuper le meme siege, et la contrainte
--  uq_leg_assignment_seat le dirait moins gentiment.
--
--  La vacation encadre l'etape : une heure de presentation avant le
--  depart, trente minutes apres l'arrivee. Ce ne sont pas des
--  chiffres de la compagnie, ce sont ceux des seeds existants (V8).
-- ------------------------------------------------------------
INSERT INTO crew.leg_assignments (id, tenant_id, leg_id, person_id, seat, ftl_verdict,
                                  duty_start, duty_end, source_type, source_ref)
SELECT DISTINCT ON (l.id, d.seat)
       md5('proto-seat-' || l.id::text || '-' || d.seat)::uuid,
       '00000000-0000-0000-0000-000000000001',
       l.id,
       d.person_id,
       d.seat,
       'OK',
       l.std - interval '1 hour',
       l.sta + interval '30 minutes',
       'seed',
       'NetPlus RFP annexe A4 — prototype iteration 33'
FROM proto_day d
JOIN ops.legs l
       ON l.business_key = 'PROTO-' || d.flight_no || '-' || to_char(d.day, 'YYYYMMDD')
WHERE d.code = 'FLT' AND d.flight_no IS NOT NULL AND d.seat IS NOT NULL
ORDER BY l.id, d.seat, d.person_id
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
--  Les vacations
--
--  Elles portent les compteurs de la fiche equipage, et c'est leur
--  presence qui fait qu'une cellule de roster est « posee sur un
--  fait » plutot que « seulement prevue » (RosterMapper.backed).
-- ------------------------------------------------------------
INSERT INTO crew.duty_periods (id, tenant_id, person_id, leg_id, kind, report_at, off_duty_at,
                               block_minutes, sectors, source_type, source_ref)
SELECT md5('proto-duty-' || la.id::text)::uuid,
       '00000000-0000-0000-0000-000000000001',
       la.person_id,
       la.leg_id,
       'FLIGHT_DUTY',
       la.duty_start,
       la.duty_end,
       (EXTRACT(epoch FROM (l.sta - l.std)) / 60)::int,
       1,
       'seed',
       'NetPlus RFP annexe A4 — prototype iteration 33'
FROM crew.leg_assignments la
JOIN ops.legs l ON l.id = la.leg_id
WHERE l.business_key LIKE 'PROTO-%'
ON CONFLICT DO NOTHING;

-- ------------------------------------------------------------
--  La version de roster, publiee
--
--  Publiee et non brouillon : c'est la semaine que l'equipage lit,
--  et c'est ce que montre le prototype. findCovering prend la plus
--  recemment publiee qui couvre le jour, donc celle-ci l'emporte
--  sur les versions de demonstration deja en base sans qu'il faille
--  y toucher.
-- ------------------------------------------------------------
INSERT INTO crew.roster_versions (id, tenant_id, label, period_start, period_end,
                                  status, published_at, source_type, source_ref)
SELECT md5('proto-roster-' || min(w.day)::text)::uuid,
       '00000000-0000-0000-0000-000000000001',
       'Week of ' || to_char(min(w.day), 'DD Mon YYYY'),
       min(w.day),
       max(w.day),
       'PUBLISHED',
       now(),
       'seed',
       'NetPlus RFP annexe A4 — prototype iteration 33'
FROM proto_week w
ON CONFLICT (tenant_id, period_start, period_end, label) DO NOTHING;

-- Les cellules. Une journee de vol pointe vers son etape et vers sa
-- vacation ; une journee de repos, de reserve ou de standby ne pointe
-- nulle part, parce qu'il n'y a rien vers quoi pointer.
INSERT INTO crew.roster_entries (id, tenant_id, roster_version_id, person_id, duty_date, code,
                                 leg_id, duty_period_id, remark, source_type, source_ref)
SELECT DISTINCT ON (d.person_id, d.day, d.code)
       md5('proto-cell-' || d.person_id::text || '-' || d.day::text || '-' || d.code)::uuid,
       '00000000-0000-0000-0000-000000000001',
       rv.id,
       d.person_id,
       d.day,
       d.code,
       la.leg_id,
       dp.id,
       CASE WHEN d.flight_no IS NOT NULL
            THEN d.flight_no || ' ' || d.dep_icao || '-' || d.arr_icao END,
       'seed',
       'NetPlus RFP annexe A4 — prototype iteration 33'
FROM proto_day d
JOIN crew.roster_versions rv
       ON rv.tenant_id = '00000000-0000-0000-0000-000000000001'
      AND rv.period_start = (SELECT min(day) FROM proto_week)
      AND rv.period_end = (SELECT max(day) FROM proto_week)
LEFT JOIN ops.legs l
       ON d.flight_no IS NOT NULL
      AND l.business_key = 'PROTO-' || d.flight_no || '-' || to_char(d.day, 'YYYYMMDD')
LEFT JOIN crew.leg_assignments la
       ON la.leg_id = l.id AND la.person_id = d.person_id
LEFT JOIN crew.duty_periods dp
       ON dp.leg_id = l.id AND dp.person_id = d.person_id
ORDER BY d.person_id, d.day, d.code, la.leg_id NULLS LAST
ON CONFLICT (roster_version_id, person_id, duty_date, code) DO NOTHING;
