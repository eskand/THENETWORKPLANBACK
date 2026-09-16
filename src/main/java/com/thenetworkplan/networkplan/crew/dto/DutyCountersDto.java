package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cumulative figures for one crew member, as of {@code computedAt}.
 *
 * <p>These are counts, not verdicts. The regulatory ceilings (100 h / 28 days,
 * 900 h / year, 60 h / 7 days) are compared to them by the FTL engine of a
 * later sprint; until that engine exists this record deliberately says how much
 * has been flown and says nothing about legality — the prototype's habit of
 * showing a green tick next to a number nobody had checked is the defect being
 * avoided here.
 */
public record DutyCountersDto(
        UUID personId,
        long blockMinutes7d,
        long blockMinutes28d,
        long blockMinutes365d,
        long dutyMinutes7d,
        long dutyMinutes28d,
        OffsetDateTime lastOffDutyAt,
        OffsetDateTime computedAt) implements Serializable {
}
