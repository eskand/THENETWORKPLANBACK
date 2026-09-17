package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Un passager tel que le tableau du dossier de vol le lit.
 *
 * <p>{@code documentValid} est calcule, jamais stocke : il vaut « le document
 * expire apres le vol ». Une colonne figee serait fausse le lendemain, et c'est
 * exactement le genre d'etat que l'audit a reproche au prototype.
 */
public record LegPassengerDto(
        UUID id,
        UUID legId,
        int seq,
        String surname,
        String givenName,
        String documentType,
        String documentNumber,
        LocalDate documentExpiry,
        String nationality,
        LocalDate dateOfBirth,
        boolean checkedIn,
        String specialRequest,
        boolean documentValid) implements Serializable {
}
