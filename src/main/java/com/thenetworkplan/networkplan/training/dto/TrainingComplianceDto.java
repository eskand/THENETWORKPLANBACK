package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The whole compliance screen in one call — same choice as the dispatch board:
 * an operator refreshing four endpoints would read four different instants.
 */
public record TrainingComplianceDto(
        List<TrainingCourseDto> courses,
        List<ComplianceRowDto> rows,
        int crewCount,
        int compliantCount,
        int expiringCount,
        int expiredCount,
        int missingCount,
        OffsetDateTime computedAt) implements Serializable {
}
