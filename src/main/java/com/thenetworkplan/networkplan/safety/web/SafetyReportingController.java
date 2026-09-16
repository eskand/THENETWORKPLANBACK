package com.thenetworkplan.networkplan.safety.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.DraftDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.MyReportDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.ReportingBoardDto;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.SaveDraftCommand;
import com.thenetworkplan.networkplan.safety.dto.ReportingDtos.SubmitReportCommand;
import com.thenetworkplan.networkplan.safety.service.SafetyReportingService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Safety Reports — filing, drafts and the reporter's own reports.
 *
 * <p>The reporter is taken from a header while there is no authentication;
 * the one seam where that becomes the identity in the token.
 */
@RestController
@RequestMapping("/v1/safety/reporting")
public class SafetyReportingController {

    private final SafetyReportingService reportingService;

    public SafetyReportingController(SafetyReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/board")
    public ReportingBoardDto board(
            @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId,
            @RequestParam(name = "reporterId", required = false) UUID reporterId) {
        return reportingService.findBoard(TenantContext.require(),
                reporterId != null ? reporterId : actorId);
    }

    @PutMapping("/drafts")
    public DraftDto saveDraft(
            @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId,
            @RequestParam(name = "reporterId", required = false) UUID reporterId,
            @RequestBody SaveDraftCommand command) {
        return reportingService.saveDraft(TenantContext.require(),
                reporterId != null ? reporterId : actorId, command);
    }

    @DeleteMapping("/drafts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(
            @PathVariable UUID id,
            @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId,
            @RequestParam(name = "reporterId", required = false) UUID reporterId) {
        reportingService.deleteDraft(TenantContext.require(),
                reporterId != null ? reporterId : actorId, id);
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public MyReportDto submit(
            @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId,
            @RequestParam(name = "reporterId", required = false) UUID reporterId,
            @Valid @RequestBody SubmitReportCommand command) {
        return reportingService.submit(TenantContext.require(),
                reporterId != null ? reporterId : actorId, command);
    }
}
