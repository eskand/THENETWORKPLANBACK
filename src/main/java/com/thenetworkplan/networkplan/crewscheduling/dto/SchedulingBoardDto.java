package com.thenetworkplan.networkplan.crewscheduling.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The Crew Scheduling screen in one call.
 *
 * <p>Same choice as the dispatch board, for the same reason: the legs, the
 * seats and the pool must describe one instant. Fetching them separately is how
 * a planner assigns someone who was taken two seconds earlier.
 */
public record SchedulingBoardDto(
        LocalDate date,
        List<SchedulingLegDto> legs,
        List<CrewCandidateDto> pool,
        int legsTotal,
        int legsFullyCrewed,
        int seatsToFill,
        int crewAvailable,
        int crewAbsent,
        OffsetDateTime computedAt) implements Serializable {
}
