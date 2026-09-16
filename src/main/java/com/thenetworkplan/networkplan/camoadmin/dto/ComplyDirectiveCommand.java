package com.thenetworkplan.networkplan.camoadmin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Recording what one registration did with one directive. */
public record ComplyDirectiveCommand(
        @NotBlank String status,
        @NotNull LocalDate compliedOn,
        String compliedRef,
        String remark) {
}
