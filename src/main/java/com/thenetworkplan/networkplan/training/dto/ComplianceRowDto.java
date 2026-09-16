package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ComplianceRowDto(
        UUID personId,
        String staffNo,
        String fullName,
        String mainRole,
        List<ComplianceCellDto> cells,
        /** Worst cell of the row: what the row is sorted and coloured by. */
        String worstStatus,
        /** Earliest expiry still ahead, so the planner knows what to book first. */
        LocalDate nextExpiry) implements Serializable {
}
