package com.thenetworkplan.networkplan.crew.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Adding a qualification to a crew file.
 *
 * <p>{@code icaoType} is mandatory for the kinds that are meaningless without a
 * type ({@code QualificationKind.requiresAircraftType()}); the service enforces
 * that rather than the annotation, because it is a domain rule and not a shape.
 */
public record SaveQualificationCommand(
        @NotNull String kind,
        String level,
        @Size(max = 8) String icaoType,
        LocalDate validFrom,
        LocalDate validTo,
        @Size(max = 120) String reference) {
}
