package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un evenement du journal d'une etape — ce que « OCC Dispatch — event
 * timeline » du menu du dossier de vol affiche.
 *
 * <p>L'annexe reconstitue sa frise a partir d'un cycle de dispatch invente
 * (creation, mise en ligne, avitaillement, equipage…) avec des statuts tires au
 * sort quand rien ne s'est passe. Ici la frise est le journal
 * {@code ops.leg_events}, qui n'existe que parce que quelque chose a eu lieu :
 * une etape sans evenement rend une liste vide, et c'est la verite.
 */
public record LegEventDto(
        UUID id,
        UUID legId,
        String kind,
        OffsetDateTime at,
        UUID actorId,
        String reason,
        String payloadBefore,
        String payloadAfter) implements Serializable {
}
