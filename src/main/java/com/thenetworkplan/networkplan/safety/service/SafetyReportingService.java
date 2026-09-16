package com.thenetworkplan.networkplan.safety.service;

import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.DraftDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.MyReportDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.ReportingBoardDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.SaveDraftCommand;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.SubmitReportCommand;
import java.util.UUID;

/**
 * Safety Reports — what a reporter can do.
 *
 * <p>Separate from SafetyService, which is what the Safety Manager does with a
 * report once it arrives. The two have different audiences and different
 * permissions, and one interface for both would eventually let a reporter see
 * an assessment that was not theirs to see.
 */
public interface SafetyReportingService {

    ReportingBoardDto findBoard(UUID tenantId, UUID reporterId);

    DraftDto saveDraft(UUID tenantId, UUID reporterId, SaveDraftCommand command);

    void deleteDraft(UUID tenantId, UUID reporterId, UUID draftId);

    MyReportDto submit(UUID tenantId, UUID reporterId, SubmitReportCommand command);
}
