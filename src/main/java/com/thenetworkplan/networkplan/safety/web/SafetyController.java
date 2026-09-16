package com.thenetworkplan.networkplan.safety.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
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
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.MonitoringSummaryDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.SafetyOverviewDto;
import com.thenetworkplan.networkplan.safety.service.SafetyOverviewService;
import com.thenetworkplan.networkplan.safety.service.SafetyService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API60 to API62 — the three safety screens.
 *
 * <p>One controller for the three, because they are three read models over
 * one domain and share every write path. The routes stay distinct so the
 * front keeps one endpoint per screen.
 */
@RestController
@RequestMapping("/v1")
public class SafetyController {

    private final SafetyService safetyService;
    private final SafetyOverviewService overviewService;

    public SafetyController(SafetyService safetyService, SafetyOverviewService overviewService) {
        this.safetyService = safetyService;
        this.overviewService = overviewService;
    }

    /** The Safety Manager dashboard — every figure computed, none stored. */
    @GetMapping("/safety/overview")
    public SafetyOverviewDto overview() {
        return overviewService.findOverview(TenantContext.require());
    }

    /**
     * Runs the live scan.
     *
     * <p>A POST because it is an action the user asks for, not a page that
     * happens to be read — even though it writes nothing. The result is true
     * at the instant it is produced and is deliberately not stored.
     */
    @PostMapping("/safety/scan")
    public MonitoringSummaryDto scan() {
        return overviewService.runScan(TenantContext.require());
    }

    /* --- Safety Manager -------------------------------------- */

    @GetMapping("/safety/board")
    public SafetyBoardDto board(@RequestParam(name = "status", required = false) String status,
                                @RequestParam(name = "windowDays", defaultValue = "365") int windowDays) {
        return safetyService.findBoard(TenantContext.require(), status, windowDays);
    }

    @GetMapping("/safety/occurrences/{id}")
    public OccurrenceDto occurrence(@PathVariable UUID id) {
        return safetyService.findOccurrence(TenantContext.require(), id);
    }

    @PostMapping("/safety/occurrences")
    @ResponseStatus(HttpStatus.CREATED)
    public OccurrenceDto report(@Valid @RequestBody ReportOccurrenceCommand command) {
        return safetyService.report(TenantContext.require(), command);
    }

    @PatchMapping("/safety/occurrences/{id}/risk")
    public OccurrenceDto assess(@PathVariable UUID id,
                                @Valid @RequestBody AssessRiskCommand command,
                                @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return safetyService.assessRisk(TenantContext.require(), id, command, actorId);
    }

    @PostMapping("/safety/occurrences/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    public ActionDto addAction(@PathVariable UUID id, @Valid @RequestBody CreateActionCommand command) {
        return safetyService.addAction(TenantContext.require(), id, command);
    }

    @PatchMapping("/safety/actions/{id}")
    public ActionDto updateAction(@PathVariable UUID id, @Valid @RequestBody UpdateActionCommand command) {
        return safetyService.updateAction(TenantContext.require(), id, command);
    }

    @PatchMapping("/safety/occurrences/{id}/close")
    public OccurrenceDto close(@PathVariable UUID id,
                               @Valid @RequestBody CloseOccurrenceCommand command,
                               @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return safetyService.close(TenantContext.require(), id, command, actorId);
    }

    @GetMapping("/safety/actions")
    public List<ActionDto> outstandingActions() {
        return safetyService.findOutstandingActions(TenantContext.require());
    }

    /* --- Safety Reports -------------------------------------- */

    @GetMapping("/safety-reports/summary")
    public ReportingSummaryDto reporting(@RequestParam(name = "windowDays", defaultValue = "365") int windowDays) {
        return safetyService.findReporting(TenantContext.require(), windowDays);
    }

    @PatchMapping("/safety-reports/occurrences/{id}/file")
    public OccurrenceDto file(@PathVariable UUID id, @Valid @RequestBody FileWithAuthorityCommand command) {
        return safetyService.fileWithAuthority(TenantContext.require(), id, command);
    }

    /* --- Safety Promotion ------------------------------------ */

    @GetMapping("/safety-promotion/board")
    public PromotionBoardDto promotion() {
        return safetyService.findPromotion(TenantContext.require());
    }

    @PostMapping("/safety-promotion/campaigns/{id}/acknowledge")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void acknowledge(@PathVariable UUID id, @Valid @RequestBody AcknowledgeCampaignCommand command) {
        safetyService.acknowledgeCampaign(TenantContext.require(), id, command);
    }
}
