package com.thenetworkplan.networkplan.safety.service;

import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.AcknowledgeCampaignCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.AssessRiskCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.CloseOccurrenceCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.CreateActionCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.FileWithAuthorityCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.ReportOccurrenceCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyCommands.UpdateActionCommand;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.ActionDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.OccurrenceDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.PromotionBoardDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.ReportingSummaryDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.SafetyBoardDto;
import java.util.List;
import java.util.UUID;

/**
 * DOM6 — the safety management system.
 *
 * <p>Three screens sit on this one domain: Safety Manager (occurrences, risk,
 * actions), Safety Reports (the filing view and the statistics) and Safety
 * Promotion (campaigns). They share the tables, so they share the service —
 * one writer per fact, three read models.
 */
public interface SafetyService {

    SafetyBoardDto findBoard(UUID tenantId, String status, int windowDays);

    OccurrenceDto findOccurrence(UUID tenantId, UUID occurrenceId);

    ReportingSummaryDto findReporting(UUID tenantId, int windowDays);

    PromotionBoardDto findPromotion(UUID tenantId);

    OccurrenceDto report(UUID tenantId, ReportOccurrenceCommand command);

    /** The level comes from the operator matrix; the caller supplies only the pair. */
    OccurrenceDto assessRisk(UUID tenantId, UUID occurrenceId, AssessRiskCommand command, UUID actorId);

    ActionDto addAction(UUID tenantId, UUID occurrenceId, CreateActionCommand command);

    ActionDto updateAction(UUID tenantId, UUID actionId, UpdateActionCommand command);

    /** Refused while an action is still outstanding. */
    OccurrenceDto close(UUID tenantId, UUID occurrenceId, CloseOccurrenceCommand command, UUID actorId);

    OccurrenceDto fileWithAuthority(UUID tenantId, UUID occurrenceId, FileWithAuthorityCommand command);

    void acknowledgeCampaign(UUID tenantId, UUID campaignId, AcknowledgeCampaignCommand command);

    List<ActionDto> findOutstandingActions(UUID tenantId);
}
