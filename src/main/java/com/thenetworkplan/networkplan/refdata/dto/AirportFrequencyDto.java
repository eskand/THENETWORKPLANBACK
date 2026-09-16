package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;

/** One published frequency: the service and what to dial. */
public record AirportFrequencyDto(
        String service,
        String mhz) implements Serializable {
}
