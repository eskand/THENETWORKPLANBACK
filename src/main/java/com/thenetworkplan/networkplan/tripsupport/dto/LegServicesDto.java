package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/** Ground services of a leg with the readiness the board shows. */
public record LegServicesDto(
        UUID legId,
        List<ServiceRequestDto> requests,
        int total,
        int confirmed,
        String readiness) implements Serializable {
}
