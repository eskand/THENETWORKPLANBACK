package com.thenetworkplan.networkplan.camoadmin.service;

import com.thenetworkplan.networkplan.camoadmin.dto.ComplyDirectiveCommand;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveApplicationDto;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveDto;
import com.thenetworkplan.networkplan.camoadmin.dto.ProgrammeTaskDto;
import com.thenetworkplan.networkplan.camoadmin.dto.SaveDirectiveCommand;
import com.thenetworkplan.networkplan.camoadmin.dto.SaveProgrammeTaskCommand;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DOM5 administration: the maintenance programme and the directives.
 *
 * <p>{@link #countOutstandingDirectives} is the door the CAMO module knocks on:
 * it asks this module a question instead of reading its tables, exactly as the
 * dispatch board asks five domains one question each.
 */
public interface CamoAdminService {

    List<ProgrammeTaskDto> findProgramme(UUID tenantId, String icaoType);

    ProgrammeTaskDto saveProgrammeTask(UUID tenantId, SaveProgrammeTaskCommand command);

    /**
     * One programme task, read by the CAMO module when it rolls the task out to
     * the registrations of its type.
     *
     * <p>The rollout itself lives in the CAMO module because it writes
     * {@code camo.aircraft_tasks}, which that module owns. Keeping the write on
     * the owning side is also what keeps the two beans out of a cycle.
     */
    ProgrammeTaskDto findProgrammeTask(UUID tenantId, UUID programmeTaskId);

    List<DirectiveDto> findDirectives(UUID tenantId, boolean outstandingOnly);

    DirectiveDto findDirective(UUID tenantId, UUID directiveId);

    DirectiveDto createDirective(UUID tenantId, SaveDirectiveCommand command);

    DirectiveApplicationDto comply(UUID tenantId, UUID applicationId, ComplyDirectiveCommand command);

    List<DirectiveApplicationDto> findByAircraft(UUID tenantId, UUID aircraftId);

    /** Outstanding AD/SB per registration, for the CAMO fleet screen. */
    Map<UUID, Integer> countOutstandingDirectives(UUID tenantId);
}
