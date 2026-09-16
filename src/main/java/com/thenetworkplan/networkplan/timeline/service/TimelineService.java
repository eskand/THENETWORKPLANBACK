package com.thenetworkplan.networkplan.timeline.service;

import com.thenetworkplan.networkplan.timeline.dto.TimelineDto;
import java.time.LocalDate;
import java.util.UUID;

public interface TimelineService {

    /**
     * The timeline of a window.
     *
     * @param from        first day of the window, UTC
     * @param days        how many days it spans, clamped to 1..31
     * @param includeIdle keep the lanes of tails that fly nothing
     * @param fleetSection restrict to one heading ("Falcon Fleet"), or null
     * @param baseIcao    restrict to tails based there, or null
     * @param statusTone  restrict to lanes carrying that state, or null
     */
    TimelineDto findTimeline(UUID tenantId,
                             LocalDate from,
                             int days,
                             boolean includeIdle,
                             String fleetSection,
                             String baseIcao,
                             String statusTone);
}
