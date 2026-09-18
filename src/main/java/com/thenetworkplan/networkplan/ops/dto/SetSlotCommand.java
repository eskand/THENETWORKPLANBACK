package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Le creneau ATC recu pour une etape.
 *
 * <p>La reference est obligatoire : sans elle, un CTOT n'est pas verifiable et
 * ne se distingue pas d'une heure estimee par le dispatcher. L'annexe la
 * demande aussi, dans une seconde boite de dialogue.
 */
public record SetSlotCommand(
        @NotNull OffsetDateTime ctot,
        String reference) {
}
