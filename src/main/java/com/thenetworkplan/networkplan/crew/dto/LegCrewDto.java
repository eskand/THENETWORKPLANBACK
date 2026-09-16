package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

/**
 * Crew picture of one leg, as the dispatch board renders it.
 *
 * @param seatsFilled  flight-deck seats actually assigned
 * @param minimumSeats flight-deck seats the leg requires
 * @param ftlStatus    worst FTL verdict among the assignments
 * @param documentStatus worst licence / medical / training state among the crew
 */
public record LegCrewDto(
        UUID legId,
        List<CrewMemberDto> members,
        int seatsFilled,
        int minimumSeats,
        boolean complete,
        String ftlStatus,
        String documentStatus) implements Serializable {

    public static LegCrewDto unassigned(UUID legId, int minimumSeats) {
        return new LegCrewDto(legId, List.of(), 0, minimumSeats, false, "UNKNOWN", "UNKNOWN");
    }

    /** True when the crew picture on its own would stop a release. */
    public boolean blocking() {
        return !complete || "BREACH".equals(ftlStatus) || "EXPIRED".equals(documentStatus);
    }
}
