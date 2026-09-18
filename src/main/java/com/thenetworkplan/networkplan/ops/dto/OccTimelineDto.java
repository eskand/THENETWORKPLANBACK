package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La frise OCC Dispatch d'une etape — « Flight Creation -> Flight Closed ».
 *
 * <p>Le calcul est celui de l'annexe ({@code computeOccTimeline}, l. 13824),
 * mais ses entrees ne sont plus des drapeaux poses sur un objet en memoire :
 * la sortie de la release vient de {@code ops.releases}, le carburant et
 * l'assistance des demandes de service, l'equipage du verdict FTL enregistre a
 * l'affectation, le creneau de {@code ops.legs.ctot}, la rotation des etapes
 * voisines du meme appareil, et la cloture de {@code mvt_sent_at}. Rien n'est
 * suppose.
 *
 * @param now            l'instant du calcul, pour que l'interface affiche
 *                       « dans 1h20 » sans dependre de l'horloge du poste
 * @param delayMinutes   retard accumule, difference entre l'ETD revise et la STD
 * @param overallDelayed vrai des qu'un evenement est en retard — le bandeau
 */
public record OccTimelineDto(
        UUID legId,
        String flightNo,
        String registration,
        String depIcao,
        String arrIcao,
        OffsetDateTime now,
        OffsetDateTime std,
        OffsetDateTime sta,
        OffsetDateTime etd,
        OffsetDateTime eta,
        long delayMinutes,
        String delayReason,
        String status,
        boolean overallDelayed,
        List<OccEventDto> events) implements Serializable {
}
