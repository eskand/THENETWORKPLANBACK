package com.thenetworkplan.networkplan.weather.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Le verdict de faible visibilite d'une etape — le bandeau du haut de l'onglet
 * FLIGHT du dossier de vol ({@code TNPLVP.ui.flightBannerHtml}, prototype
 * l. 74774).
 *
 * @param severity          GREEN / AMBER / RED / GREY. GREEN est silencieux :
 *                          l'annexe ne dessine pas de bandeau, et un bandeau
 *                          permanent apprend a ne plus lire le bandeau.
 * @param operationalStatus ABOVE_MINIMA / APPROACHING_MINIMA / BELOW_MINIMA /
 *                          INSUFFICIENT_DATA — le vocabulaire de l'annexe.
 * @param reason            la phrase que le bandeau affiche.
 * @param detail            le complement chiffre, quand il y en a un
 *                          (« VIS 400 m — LVTO conditions »).
 * @param station           l'aerodrome qui porte le verdict : c'est celui que
 *                          le bouton du bandeau ouvre.
 * @param stations          le detail par aerodrome, pour le panneau « Open LVP ».
 */
public record LegLvpDto(
        UUID legId,
        String severity,
        String operationalStatus,
        String reason,
        String detail,
        String station,
        List<StationLvpDto> stations) implements Serializable {
}
