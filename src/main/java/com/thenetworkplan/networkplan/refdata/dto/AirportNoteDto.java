package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record AirportNoteDto(
        UUID id,
        String kind,
        String title,
        String detail,
        LocalDate validFrom,
        LocalDate validTo,
        String severity,
        boolean inForce) implements Serializable {
}
