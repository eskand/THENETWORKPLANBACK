package com.thenetworkplan.networkplan.techlog.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.techlog.dto.DefectDto;
import com.thenetworkplan.networkplan.techlog.dto.ReportDefectCommand;
import com.thenetworkplan.networkplan.techlog.dto.ResolveDefectCommand;
import com.thenetworkplan.networkplan.techlog.dto.SaveTechLogEntryCommand;
import com.thenetworkplan.networkplan.techlog.dto.TechLogBoardDto;
import com.thenetworkplan.networkplan.techlog.dto.TechLogEntryDto;
import com.thenetworkplan.networkplan.techlog.service.TechLogService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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

/** API33 — Tech Log: pages, signature and defects. */
@RestController
@RequestMapping("/v1/tech-log")
public class TechLogController {

    private final TechLogService techLogService;

    public TechLogController(TechLogService techLogService) {
        this.techLogService = techLogService;
    }

    /** Defaults to the last thirty days, which is what a tech log review covers. */
    @GetMapping("/pages")
    public List<TechLogEntryDto> pages(
            @RequestParam(name = "aircraftId", required = false) UUID aircraftId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(30);
        return techLogService.findPages(TenantContext.require(), aircraftId, start, end);
    }

    @GetMapping("/pages/{id}")
    public TechLogEntryDto page(@PathVariable UUID id) {
        return techLogService.findPage(TenantContext.require(), id);
    }

    @PostMapping("/pages")
    @ResponseStatus(HttpStatus.CREATED)
    public TechLogEntryDto createPage(@Valid @RequestBody SaveTechLogEntryCommand command) {
        return techLogService.createPage(TenantContext.require(), command);
    }

    @PostMapping("/pages/{id}/sign")
    public TechLogEntryDto sign(@PathVariable UUID id,
                                @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return techLogService.sign(TenantContext.require(), id, actorId);
    }

    /**
     * The board: the defect picture of the fleet with its figures.
     *
     * <p>The list screen reads this; {@code /defects} stays for the callers
     * that want the rows alone.
     */
    @GetMapping("/board")
    public TechLogBoardDto board() {
        return techLogService.findBoard(TenantContext.require());
    }

    @GetMapping("/defects")
    public List<DefectDto> defects(@RequestParam(name = "aircraftId", required = false) UUID aircraftId,
                                   @RequestParam(name = "openOnly", defaultValue = "true") boolean openOnly) {
        return techLogService.findDefects(TenantContext.require(), aircraftId, openOnly);
    }

    @PostMapping("/pages/{id}/defects")
    @ResponseStatus(HttpStatus.CREATED)
    public DefectDto reportDefect(@PathVariable UUID id, @Valid @RequestBody ReportDefectCommand command) {
        return techLogService.reportDefect(TenantContext.require(), id, command);
    }

    @PostMapping("/defects")
    @ResponseStatus(HttpStatus.CREATED)
    public DefectDto reportStandaloneDefect(@Valid @RequestBody ReportDefectCommand command) {
        return techLogService.reportDefect(TenantContext.require(), null, command);
    }

    @PatchMapping("/defects/{id}/resolve")
    public DefectDto resolve(@PathVariable UUID id, @Valid @RequestBody ResolveDefectCommand command) {
        return techLogService.resolveDefect(TenantContext.require(), id, command);
    }
}
