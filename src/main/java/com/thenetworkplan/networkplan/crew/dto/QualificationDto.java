package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record QualificationDto(
        UUID id,
        String kind,
        String level,
        String icaoType,
        LocalDate validFrom,
        LocalDate validTo,
        /** Same four states as a crew document: the rule is shared, not copied. */
        String status,
        String reference) implements Serializable {
}
