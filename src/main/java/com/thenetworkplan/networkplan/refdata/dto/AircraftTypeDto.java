package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;

public record AircraftTypeDto(
        String icaoType,
        String manufacturer,
        String model,
        String wakeCategory,
        Integer mtowKg,
        Integer minRunwayFt,
        Integer maxPax,
        Integer rangeNm,
        Integer cruiseTasKt,
        boolean etopsApplicable) implements Serializable {
}
