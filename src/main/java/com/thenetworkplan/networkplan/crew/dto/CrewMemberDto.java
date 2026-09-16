package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CrewMemberDto(
        UUID assignmentId,
        UUID personId,
        String staffNo,
        String fullName,
        String seat,
        String mainRole,
        String ftlVerdict,
        String ftlReason,
        LocalDate licenceExpiry,
        LocalDate medicalExpiry,
        LocalDate trainingExpiry,
        String documentStatus,
        OffsetDateTime dutyStart,
        OffsetDateTime dutyEnd,
        OffsetDateTime checkedInAt) implements Serializable {
}
