package com.thenetworkplan.networkplan.dispatch.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Everything the Dispatch screen needs, in one response.
 *
 * <p>One call rather than a dozen: the screen shows the whole operating picture
 * at once, and an OCC refreshing six endpoints would see six different instants.
 *
 * @param tabCounts  row count per tab, so the tabs can be labelled without
 *                   re-querying
 * @param generatedAt the instant the picture was assembled, shown to the user
 */
public record DispatchBoardDto(
        LocalDate date,
        DispatchKpiDto kpi,
        List<DispatchRowDto> rows,
        List<String> fleetTypes,
        List<String> bases,
        Map<String, Integer> tabCounts,
        OffsetDateTime generatedAt) implements Serializable {
}
