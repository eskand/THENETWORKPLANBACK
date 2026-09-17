package com.thenetworkplan.networkplan.ops.domain;

/**
 * La nature commerciale d'une etape — l'axe qui decide de la lettre de la case 8
 * du plan de vol OACI.
 *
 * <p>A ne pas confondre avec {@link FlightType}, qui dit ce que l'etape
 * transporte et pourquoi elle vole. Un vol passagers peut etre programme ou
 * affrete ; c'est ici que la difference se lit.
 */
public enum CommercialType {
    /** Programme, publie a l'avance — case 8 : S. */
    SCHEDULED,
    /** Hors programme : affretement, mise en place, convoyage — case 8 : N. */
    NON_SCHEDULED,
    /** Aviation generale, pour compte propre — case 8 : G. */
    PRIVATE,
    /** Vol d'Etat, sanitaire ou humanitaire — case 8 : X. */
    STATE;

    /**
     * La lettre de la case 8, telle que l'annexe la derive
     * ({@code tnpOptypeLetter}, prototype l. 15087).
     *
     * <p>L'evacuation sanitaire l'emporte sur la nature commerciale : un
     * transport sanitaire affrete se depose en X, pas en N. L'entrainement et
     * la maintenance ne figurent dans aucune des quatre categories nommees et
     * se deposent donc en X, « autre ».
     */
    public String flightPlanLetter(FlightType flightType) {
        if (flightType == FlightType.AMBULANCE) {
            return "X";
        }
        if (flightType == FlightType.TRAINING || flightType == FlightType.MAINTENANCE) {
            return this == PRIVATE ? "G" : "X";
        }
        return switch (this) {
            case SCHEDULED -> "S";
            case NON_SCHEDULED -> "N";
            case PRIVATE -> "G";
            case STATE -> "X";
        };
    }
}
