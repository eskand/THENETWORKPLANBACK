package com.thenetworkplan.networkplan.ops.domain;

/**
 * Les documents du dossier de vol — les cinq lignes de l'onglet TRIP FOLDER de
 * l'annexe (l. 16524), plus la GENDEC que son modale produit et « OTHER » pour
 * ce qu'un equipage depose sans que la liste l'ait prevu.
 */
public enum LegDocumentKind {
    FPL,
    OFP,
    WEIGHT_BALANCE,
    NOTOC,
    FUEL_RECEIPT,
    GENDEC,
    OTHER
}
