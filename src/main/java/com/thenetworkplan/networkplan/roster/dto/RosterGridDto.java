package com.thenetworkplan.networkplan.roster.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** The whole grid in one call: the version, its days, and one row per person. */
public record RosterGridDto(
        RosterVersionDto version,
        List<LocalDate> days,
        List<RosterRowDto> rows) implements Serializable {
}
