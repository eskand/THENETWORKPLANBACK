package com.thenetworkplan.networkplan.camo.dto;

import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveApplicationDto;
import java.io.Serializable;
import java.util.List;

/**
 * The CAMO file of one registration.
 *
 * <p>Directives are read through the CAMO Admin service, not from its tables:
 * a module answers for its own rows.
 *
 * <p>{@code arc} is the certificate in force and {@code arcHistory} the ones it
 * replaced, newest first. They are separate fields rather than one list with a
 * flag, because the screen asks two different questions of them: may this
 * aircraft fly today, and has its cover been continuous.
 */
public record AircraftCamoDto(
        FleetStatusRowDto summary,
        List<DueItemDto> tasks,
        List<UtilisationRowDto> recentUtilisation,
        List<DirectiveApplicationDto> directives,
        ArcDto arc,
        List<ArcDto> arcHistory,
        List<LifeLimitedPartDto> lifeLimitedParts,
        List<WorkOrderDto> workOrders) implements Serializable {
}
