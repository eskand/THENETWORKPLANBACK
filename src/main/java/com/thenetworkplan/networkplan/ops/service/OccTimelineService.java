package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.OccTimelineDto;
import java.util.UUID;

/**
 * La frise « OCC Dispatch » d'une etape, de sa creation a sa cloture.
 *
 * <p>C'est {@code computeOccTimeline()} de l'annexe (prototype l. 13824), porte
 * sur des faits enregistres : la sortie de release vient de {@code ops.releases},
 * le carburant et l'assistance des demandes de service, l'equipage du verdict
 * FTL pose a l'affectation, le creneau de {@code ops.legs.ctot}, l'escale des
 * etapes voisines du meme appareil. La ou l'annexe posait un drapeau en memoire,
 * il y a ici une ligne en base — et donc un etat qui survit au rechargement.
 */
public interface OccTimelineService {

    OccTimelineDto timeline(UUID tenantId, UUID legId);
}
