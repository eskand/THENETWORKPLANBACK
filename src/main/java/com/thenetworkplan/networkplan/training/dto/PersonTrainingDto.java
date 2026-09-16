package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** The training file of one crew member: what is held, what is booked. */
public record PersonTrainingDto(
        UUID personId,
        String staffNo,
        String fullName,
        List<TrainingRecordDto> records,
        List<TrainingEnrolmentDto> enrolments) implements Serializable {
}
