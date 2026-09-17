package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One duty period, with the person already resolved.
 *
 * <p><b>Why this exists next to {@link DutyPeriodDto}.</b> That one is a duty
 * seen from inside one crew file: the screen showing it already knows whose it
 * is. The FTL sheet, the roster report and the duty report read the whole crew
 * at once and have to print a name against every line — joining the two lists
 * in the caller would put a lookup in a loop, and the repository already
 * fetches the person.
 *
 * @param restBeforeMinutes minutes between the end of this person's previous
 *                          duty and this report time. Null for the first duty
 *                          of the window, where the previous one lies outside
 *                          it: a rest period nobody measured is not a long
 *                          rest, and printing one would be the FTL equivalent
 *                          of a green tick over a missing input.
 * @param countsAsDuty      whether this period consumes duty time.
 *                          {@link com.thenetworkplan.networkplan.crew.domain.DutyKind#countsAsDuty()}
 *                          is the single answer to that question, and it is
 *                          carried here rather than re-derived from the kind
 *                          string by every reader: rest and days off are
 *                          recorded as duty periods, and an FTL total that
 *                          summed them would put every crew member over the
 *                          190 h ceiling.
 * @param maxFdpMinutes     the maximum flight duty period allowed for this
 *                          report time and sector count under EASA
 *                          ORO.FTL.205 table 2, unacclimatised crew. Null when
 *                          the duty is not a flight duty, where the table does
 *                          not apply.
 */
public record CrewDutyDto(
        UUID id,
        UUID personId,
        String staffNo,
        String fullName,
        String mainRole,
        String baseIcao,
        UUID legId,
        String kind,
        String rosterCode,
        OffsetDateTime reportAt,
        OffsetDateTime offDutyAt,
        long dutyMinutes,
        Integer blockMinutes,
        int sectors,
        String remark,
        Long restBeforeMinutes,
        Integer maxFdpMinutes,
        boolean countsAsDuty) implements Serializable {
}
