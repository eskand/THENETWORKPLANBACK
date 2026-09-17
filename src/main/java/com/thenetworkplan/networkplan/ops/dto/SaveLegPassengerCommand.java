package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Creer ou corriger une ligne du manifeste. */
public record SaveLegPassengerCommand(
        @NotBlank @Size(max = 120) String surname,
        @Size(max = 120) String givenName,
        @Size(max = 40) String documentType,
        @Size(max = 60) String documentNumber,
        LocalDate documentExpiry,
        @Size(max = 60) String nationality,
        LocalDate dateOfBirth,
        boolean checkedIn,
        @Size(max = 300) String specialRequest) {
}
