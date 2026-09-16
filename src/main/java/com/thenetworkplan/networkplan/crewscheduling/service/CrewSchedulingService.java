package com.thenetworkplan.networkplan.crewscheduling.service;

import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crewscheduling.dto.AssignSeatCommand;
import com.thenetworkplan.networkplan.crewscheduling.dto.SchedulingBoardDto;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DOM4 read model of the Crew Scheduling screen.
 *
 * <p>Owns no table, exactly like {@code DispatchBoardService}: it asks DOM1 for
 * the legs of the day and DOM4 for the crew, and composes one answer.
 */
public interface CrewSchedulingService {

    SchedulingBoardDto findBoard(UUID tenantId, LocalDate date, String role);

    CrewMemberDto assign(UUID tenantId, UUID legId, AssignSeatCommand command, UUID actorId);

    void unassign(UUID tenantId, UUID assignmentId);
}
