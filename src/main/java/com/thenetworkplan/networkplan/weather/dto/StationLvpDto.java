package com.thenetworkplan.networkplan.weather.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Le verdict de faible visibilite d'UN aerodrome de l'etape — ce que le panneau
 * « Open LVP » du bandeau detaille.
 *
 * <p>Il porte la mesure ET sa provenance : un verdict de faible visibilite qui
 * ne dit pas de quel METAR il sort ne peut pas etre contredit par l'equipage,
 * et c'est exactement ce qu'on lui demande de pouvoir faire.
 *
 * @param role        DEPARTURE ou DESTINATION.
 * @param state       LIVE / STALE / NO_OBSERVATION — l'etat de l'observation.
 * @param assessed    ce que le produit a pu trancher, en une phrase.
 * @param notAssessed ce qu'il n'a PAS pu trancher, et pourquoi. Jamais vide ici
 *                    tant que la table des minima approuves n'existe pas.
 */
public record StationLvpDto(
        String icao,
        String role,
        String severity,
        String operationalStatus,
        String state,
        Integer visibilityM,
        Integer ceilingFt,
        boolean cavok,
        OffsetDateTime observedAt,
        Long ageMinutes,
        String rawText,
        String assessed,
        String notAssessed) implements Serializable {
}
