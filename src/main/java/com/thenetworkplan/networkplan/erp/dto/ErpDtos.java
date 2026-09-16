package com.thenetworkplan.networkplan.erp.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The read models of the ERP screen. */
public final class ErpDtos {

    private ErpDtos() {
    }

    public record ErpRoleDto(
            UUID id,
            String roleCode,
            String roleTitle,
            /** What the cell covers, in plain words. */
            String scope,
            /** The colour the cell carries on every screen that shows it. */
            String colour,
            UUID holderUserId,
            UUID deputyUserId,
            String phone,
            String responsibilities,
            int callOrder,
            /** A role without a deputy is a single point of failure. */
            boolean singlePointOfFailure) implements Serializable {
    }

    public record ErpActivationDto(
            UUID id,
            String reference,
            String kind,
            /** 0 to 4 — the console is coloured by it. */
            short level,
            String levelName,
            String eventLabel,
            String initiatedByName,
            String initiatedByRole,
            String concurredByName,
            String concurredByRole,
            String overrideReason,
            String stoodDownBy,
            UUID legId,
            OffsetDateTime activatedAt,
            OffsetDateTime stoodDownAt,
            Long durationMinutes,
            boolean open,
            String situation) implements Serializable {
    }

    /** One rehearsal of the plan. */
    public record ErpExerciseDto(
            UUID id,
            String reference,
            String exerciseType,
            String scenario,
            LocalDate heldOn,
            Short level,
            int participants,
            int findings,
            String status,
            String lessons) implements Serializable {
    }

    public record ErpBoardDto(
            UUID planId,
            String code,
            String title,
            String revision,
            LocalDate approvedOn,
            LocalDate reviewDueOn,
            Long daysToReview,
            boolean reviewOverdue,
            String summary,
            List<ErpRoleDto> roles,
            List<ErpActivationDto> activations,
            /** L'activation en cours, ou null : le plan est arme. */
            ErpActivationDto active,
            List<ErpExerciseDto> exercises,
            int rolesWithoutDeputy,
            int exercisesLast12Months,
            OffsetDateTime lastExerciseAt,
            boolean activationOpen,
            OffsetDateTime computedAt) implements Serializable {
    }
}
