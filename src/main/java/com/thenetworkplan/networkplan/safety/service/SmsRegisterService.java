package com.thenetworkplan.networkplan.safety.service;

import com.thenetworkplan.networkplan.safety.dto.SmsRegisterCommands.AnswerQueryCommand;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterCommands.AskQueryCommand;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.HazardRegisterDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.NotificationCentreDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.QueryDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.RexDto;
import java.util.List;
import java.util.UUID;

/**
 * The hazard register, the experience library and the notification centre —
 * the three parts of the safety management system that had no table behind
 * them.
 */
public interface SmsRegisterService {

    /** The register with its matrix, populated. */
    HazardRegisterDto findRegister(UUID tenantId);

    /** Records that a hazard was reviewed, and sets the next review date. */
    HazardRegisterDto recordReview(UUID tenantId, UUID hazardId, int cycleDays);

    List<RexDto> findRexLibrary(UUID tenantId, String reader);

    List<RexDto> markRexRead(UUID tenantId, UUID rexId, String reader);

    /** The questions put to reporters. {@code reporter} null returns all. */
    List<QueryDto> findQueries(UUID tenantId, String reporter, boolean openOnly);

    QueryDto askQuery(UUID tenantId, UUID occurrenceId, AskQueryCommand command);

    QueryDto answerQuery(UUID tenantId, UUID occurrenceId, AnswerQueryCommand command);

    NotificationCentreDto findNotifications(UUID tenantId);

    NotificationCentreDto markRead(UUID tenantId, UUID notificationId);

    NotificationCentreDto markAllRead(UUID tenantId);
}
