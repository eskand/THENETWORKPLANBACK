package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record AbsenceDto(
        UUID id,
        UUID personId,
        String personName,
        String kind,
        LocalDate startsOn,
        LocalDate endsOn,
        String reason) implements Serializable {
}
