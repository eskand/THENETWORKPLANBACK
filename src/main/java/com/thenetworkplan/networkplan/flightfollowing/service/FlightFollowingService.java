package com.thenetworkplan.networkplan.flightfollowing.service;

import com.thenetworkplan.networkplan.flightfollowing.dto.FollowingBoardDto;
import com.thenetworkplan.networkplan.flightfollowing.dto.PositionDto;
import com.thenetworkplan.networkplan.flightfollowing.dto.ReportPositionCommand;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Flight Following — where the aircraft are, and how sure we are of it. */
public interface FlightFollowingService {

    FollowingBoardDto findBoard(UUID tenantId, LocalDate date);

    List<PositionDto> findTrack(UUID tenantId, UUID legId);

    PositionDto report(UUID tenantId, ReportPositionCommand command, UUID actorId);
}
