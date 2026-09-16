package com.thenetworkplan.networkplan.airworthiness.service;

import com.thenetworkplan.networkplan.airworthiness.dto.AircraftDto;
import com.thenetworkplan.networkplan.airworthiness.dto.AirworthinessSnapshotDto;
import com.thenetworkplan.networkplan.airworthiness.dto.ChangeAircraftStatusCommand;
import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DOM5 read and write surface used by dispatch.
 *
 * <p>Every method takes the tenant explicitly rather than reading a thread-local:
 * the dispatch board calls these from virtual threads it owns, where a
 * thread-local would be empty.
 */
public interface AircraftService {

    List<AircraftDto> findFleet(UUID tenantId);

    /** Tails that are not released to service: the rows dispatch must act on. */
    List<AircraftDto> findGrounded(UUID tenantId);

    AirworthinessSnapshotDto findAirworthiness(UUID tenantId, UUID aircraftId);

    /** Open MEL items of the whole fleet, grouped by aircraft id, in one query. */
    Map<UUID, List<MelItemDto>> findOpenMelByAircraft(UUID tenantId);

    AircraftDto changeStatus(UUID tenantId, UUID aircraftId, ChangeAircraftStatusCommand command);
}
