package com.thenetworkplan.networkplan.training.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Closing an enrolment.
 *
 * <p>Setting it to {@code ATTENDED} is what creates the training record and its
 * expiry — the two are one transaction, so a certificate cannot exist without
 * the attendance that produced it, and attendance cannot be recorded without
 * updating the file.
 */
public record UpdateEnrolmentCommand(
        @NotNull String status,
        BigDecimal score) {
}
