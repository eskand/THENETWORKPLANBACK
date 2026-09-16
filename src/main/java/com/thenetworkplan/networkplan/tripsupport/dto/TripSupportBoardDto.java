package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** The NetPlus Services screen in one call. */
public record TripSupportBoardDto(
        LocalDate date,
        List<TripSupportRowDto> rows,
        int legs,
        int legsFullyServiced,
        int requestsPending,
        int permitsOutstanding,
        int legsAtRisk,
        OffsetDateTime computedAt) implements Serializable {
}
