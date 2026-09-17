package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * L'onglet PAX d'une etape : les trois compteurs de l'annexe et la liste.
 *
 * <p>{@code totalPax} est le nombre annonce sur l'etape ({@code ops.legs
 * .pax_count}) ; {@code onManifest} est le nombre de lignes reellement saisies.
 * Les deux different tant que le manifeste n'est pas complet, et c'est
 * precisement ce que l'agent doit voir — l'annexe affiche les deux pour cette
 * raison.
 */
public record LegPaxDto(
        UUID legId,
        int totalPax,
        int checkedIn,
        int onManifest,
        int documentsNotValid,
        List<String> specialRequests,
        List<LegPassengerDto> passengers) implements Serializable {
}
