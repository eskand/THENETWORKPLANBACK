package com.thenetworkplan.networkplan.crewscheduling.dto;

import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** One leg of the day, seen from crew scheduling. */
public record SchedulingLegDto(
        UUID legId,
        String flightNo,
        String registration,
        String icaoType,
        /**
         * The aircraft family — FALCON, CITATION, LINEAGE — from
         * {@link com.thenetworkplan.networkplan.crew.service.TypeFamily}.
         *
         * <p>It travels with the leg so the roster editor can offer "the
         * Citation flights of this day" without the browser re-deriving a
         * family from an ICAO designator. The rule has one home; a second copy
         * in JavaScript would drift from it the first time a type is added.
         */
        String typeFamily,
        String depIcao,
        String arrIcao,
        OffsetDateTime std,
        OffsetDateTime sta,
        String status,
        List<CrewMemberDto> crew,
        int seatsFilled,
        int minimumSeats,
        boolean complete,
        String ftlStatus,
        String documentStatus) implements Serializable {
}
