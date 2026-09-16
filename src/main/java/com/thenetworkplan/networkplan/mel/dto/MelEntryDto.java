package com.thenetworkplan.networkplan.mel.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One deferred item on one registration, as the MEL screen shows it.
 *
 * <p>{@code dueStatus} is {@code UNKNOWN} when the category leaves the interval
 * to the MEL remark: the line then says "interval per MEL remark" instead of
 * showing a date nobody computed — the audit's {@code MEL dueDate '—'} without
 * the pretence that it is fine.
 */
public record MelEntryDto(
        UUID id,
        UUID aircraftId,
        String registration,
        String icaoType,
        String reference,
        /** From the defect that raised it, or from the library line behind it. */
        String ataChapter,
        /** The system the library line covers — not the defect, the system. */
        String systemName,
        String melCategory,
        String title,
        String limitation,
        OffsetDateTime raisedAt,
        OffsetDateTime dueAt,
        Long daysRemaining,
        String dueStatus,
        boolean blocksDispatch,
        boolean placardRequired,
        boolean placardFitted,
        String operationalProcedure,
        /**
         * Where the deferral came from: a tech log page, or the airworthiness
         * record itself. Two routes into the same list, and the Hold Item List
         * has to be able to say which one a line arrived by.
         */
        String source) implements Serializable {
}
