package com.thenetworkplan.networkplan.training.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TrainingSessionDto(
        UUID id,
        String courseCode,
        String courseTitle,
        String category,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String location,
        int capacity,
        int booked,
        int seatsLeft,
        String instructorName,
        String status,
        List<TrainingEnrolmentDto> enrolments) implements Serializable {
}
