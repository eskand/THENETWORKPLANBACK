-- ============================================================
--  V29 — deux codes de vacation de plus : DH et SIM.
--
--  POURQUOI. L'editeur de case du prototype (annexe A4, modale
--  openCellModal) propose DIX types de vacation ; la base n'en
--  connaissait que neuf. Les deux manquants sont « Dead Head »
--  et « Simulator », et ils manquaient jusque dans la legende :
--  le prototype y annonce « Dead Head » sans qu'aucune cellule
--  puisse le porter.
--
--  CE QUE CHACUN VEUT DIRE.
--    DH  — mise en place PASSAGER sur un vol, commercial ou non,
--          pour rejoindre ou quitter un poste. La personne est en
--          service mais ne fait pas partie de l'equipage du vol :
--          c'est ce qui la distingue de POS, ou elle est aux
--          commandes d'une mise en place.
--    SIM — seance de simulateur. Distincte de TRG, qui couvre le
--          stage au sol : une OPC/LPC au simulateur et une journee
--          de cours ne se comptent pas pareil dans les recences,
--          et les confondre dans TRG rendait l'une invisible.
--
--  CE QUI NE BOUGE PAS. Aucune cellule existante n'est modifiee.
--  On remplace la contrainte de controle par la meme, augmentee
--  des deux valeurs : les lignes deja ecrites la satisfont toutes,
--  et la migration ne peut donc pas echouer sur l'existant.
--
--  DUTY_PERIODS. crew.duty_periods garde ses sept kinds : une mise
--  en place passager est une vacation de type POSITIONING, et un
--  simulateur une vacation de type TRAINING. Le code de roster dit
--  ce qui etait PREVU, le kind dit ce qui est COMPTE — les deux
--  n'ont pas la meme granularite, et c'est voulu.
-- ============================================================

-- IF EXISTS : la migration doit pouvoir etre rejouee. Elle est appliquee a la
-- main pour verification avant que Flyway ne la voie, et Flyway la rejoue
-- ensuite au demarrage ; sans cela le second passage echouerait sur un DROP
-- d une contrainte deja retiree.
ALTER TABLE crew.roster_entries DROP CONSTRAINT IF EXISTS ck_roster_code;

ALTER TABLE crew.roster_entries ADD CONSTRAINT ck_roster_code CHECK (code IN (
    'FLT', 'SBY', 'POS', 'TRG', 'OFF', 'LVE', 'SICK', 'OFFICE', 'RES', 'DH', 'SIM'));
