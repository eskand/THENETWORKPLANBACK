package com.thenetworkplan.networkplan.techlog.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Reporting a defect, from a page or directly against a registration. */
public record ReportDefectCommand(
        UUID aircraftId,
        String ataChapter,
        @NotBlank String description,
        UUID reportedBy) {
}
