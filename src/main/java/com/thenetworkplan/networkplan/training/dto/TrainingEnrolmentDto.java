package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

public record TrainingEnrolmentDto(
        UUID id,
        UUID sessionId,
        UUID personId,
        String staffNo,
        String personName,
        String status,
        BigDecimal score) implements Serializable {
}
