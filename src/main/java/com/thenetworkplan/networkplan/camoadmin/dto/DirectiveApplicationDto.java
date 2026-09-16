package com.thenetworkplan.networkplan.camoadmin.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record DirectiveApplicationDto(
        UUID id,
        UUID aircraftId,
        String registration,
        String status,
        LocalDate compliedOn,
        String compliedRef,
        String remark) implements Serializable {
}
