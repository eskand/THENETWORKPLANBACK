package com.thenetworkplan.networkplan.erp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** The two commands of the ERP module. */
public final class ErpCommands {

    private ErpCommands() {
    }

    /**
     * Activating the plan.
     *
     * <p>{@code kind} is mandatory and has no default: an exercise and a real
     * activation must never be recorded as the same thing by omission.
     */
    public record ActivateCommand(
            @NotBlank String kind,
            @NotBlank String situation,
            UUID legId,
            /** 0 to 4. A level 1 is an OCC matter; a level 4 opens a family centre. */
            @NotNull @Min(0) @Max(4) Short level,
            @NotBlank String eventLabel,
            /**
             * Who initiates, and who concurs.
             *
             * <p>Both are required for a real activation, or the Accountable
             * Manager's override with its reason. The rule is the plan's, not
             * a preference of this interface, and the database enforces it too.
             */
            @NotBlank String initiatedByName,
            String initiatedByRole,
            String concurredByName,
            String concurredByRole,
            String overrideReason,
            /** The case from the catalogue the assessment settled on. */
            String eventCode,
            /** What is known of the aircraft at the moment of activation. */
            String flight,
            String registration,
            String aircraftType,
            String origin,
            String destination,
            String pob) {
    }

    public record StandDownCommand(
            @NotBlank String outcome,
            /** Who ordered the stand-down. It goes on the record. */
            String stoodDownBy) {
    }
}
