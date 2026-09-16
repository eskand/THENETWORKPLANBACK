package com.thenetworkplan.networkplan.roster.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * One crew member's line on the grid.
 *
 * @param typeRating the aircraft family the person is rated on — FALCON,
 *                   CITATION, LEGACY — read from {@code crew.qualifications},
 *                   never guessed from the rank. Null when the crew file holds
 *                   no type rating, and the screen then shows the rank alone: a
 *                   missing rating is a gap in the file, not a licence to
 *                   invent one. A person rated on several families carries the
 *                   one whose rating runs longest.
 */
public record RosterRowDto(
        UUID personId,
        String staffNo,
        String fullName,
        String mainRole,
        String typeRating,
        List<RosterCellDto> cells,
        int workingDays,
        int daysOff,
        /**
         * Block minutes flown over the period, summed from the duty periods
         * that actually record flying — never from the roster codes, which say
         * what a day is for and not how long it lasted. Zero is a fact: it
         * means nothing was flown, not that nothing is known.
         */
        long blockMinutes) implements Serializable {
}
