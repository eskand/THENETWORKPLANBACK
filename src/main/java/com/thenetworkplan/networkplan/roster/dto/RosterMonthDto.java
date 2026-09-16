package com.thenetworkplan.networkplan.roster.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * One calendar month of roster, whatever versions cover it.
 *
 * <p>A crew planner reads a month, not a version. Versions are how the operator
 * keeps a published roster immutable — a correction opens a new one — but that
 * is bookkeeping, and no one plans in it: the approved prototype navigates
 * « September 2026 », and so does this.
 *
 * <p>So the month is assembled, not stored. Every version overlapping it is
 * read, and the cells are laid on the calendar. Where two versions speak about
 * the same day, the published one wins: what the crew was told outranks what
 * someone is still drafting. {@link RosterCellDto#draft()} carries which one
 * spoke, so the screen can draw the difference rather than hide it.
 *
 * @param month    the month rendered, {@code YYYY-MM}
 * @param versions every version that covers any day of the month, newest
 *                 first — the screen needs them to know what it may write to
 *                 and what it may publish
 */
public record RosterMonthDto(
        String month,
        List<LocalDate> days,
        List<RosterRowDto> rows,
        List<RosterVersionDto> versions) implements Serializable {
}
