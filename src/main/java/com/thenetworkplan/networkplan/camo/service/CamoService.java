package com.thenetworkplan.networkplan.camo.service;

import com.thenetworkplan.networkplan.camo.dto.AircraftCamoDto;
import com.thenetworkplan.networkplan.camo.dto.CompleteTaskCommand;
import com.thenetworkplan.networkplan.camo.dto.DueItemDto;
import com.thenetworkplan.networkplan.camo.dto.FleetStatusRowDto;
import com.thenetworkplan.networkplan.camo.dto.LifeLimitedPartDto;
import com.thenetworkplan.networkplan.camo.dto.WorkOrderDto;
import java.util.List;
import java.util.UUID;

/**
 * CAMO — continuing airworthiness of the fleet.
 *
 * <p>A read model over four owners: its own tasks and utilisation, the MEL
 * items of DOM5, the defects of the tech log and the directives of CAMO Admin.
 * Like the dispatch board, it asks each module one question instead of reading
 * their tables.
 */
public interface CamoService {

    List<FleetStatusRowDto> findFleetStatus(UUID tenantId);

    /**
     * @param horizonDays tasks whose calendar limit falls inside this window,
     *                    plus everything already overdue whatever its date
     */
    List<DueItemDto> findDueList(UUID tenantId, int horizonDays);

    AircraftCamoDto findAircraft(UUID tenantId, UUID aircraftId);

    /**
     * Every life-limited part still fitted, shortest life first.
     *
     * <p>Fleet-wide and unfiltered: the question this tab answers is "what comes
     * off next, on which aircraft", and a per-registration view cannot answer it.
     */
    List<LifeLimitedPartDto> findLifeLimitedParts(UUID tenantId);

    /** Work orders still running, nearest target first, undated ones last. */
    List<WorkOrderDto> findOpenWorkOrders(UUID tenantId);

    /** Signs off a task and writes the next due from the programme interval. */
    DueItemDto completeTask(UUID tenantId, UUID taskId, CompleteTaskCommand command);

    /** Applies a programme task to every registration of its type that lacks it. */
    int rolloutProgrammeTask(UUID tenantId, UUID programmeTaskId);
}
