package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.util.UUID;

public record TrainingCourseDto(
        UUID id,
        String code,
        String title,
        String category,
        Integer validityMonths,
        boolean mandatory,
        String authorityRef) implements Serializable {
}
