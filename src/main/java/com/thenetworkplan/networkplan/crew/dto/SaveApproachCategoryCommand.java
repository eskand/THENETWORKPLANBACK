package com.thenetworkplan.networkplan.crew.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Grading a pilot's low-visibility approach category.
 *
 * <p>{@code validTo} is optional in shape and mandatory in rule: the service
 * refuses anything above CAT I without one, because a category that never
 * expires would go on asserting a training nobody renewed. Stated in the
 * service rather than in an annotation, since it depends on the category.
 */
public record SaveApproachCategoryCommand(
        @NotNull String category,
        LocalDate validFrom,
        LocalDate validTo,
        @Size(max = 120) String reference) {
}
