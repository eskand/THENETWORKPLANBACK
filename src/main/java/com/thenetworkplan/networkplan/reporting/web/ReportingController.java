package com.thenetworkplan.networkplan.reporting.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportDefinitionDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportResultDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportRunDto;
import com.thenetworkplan.networkplan.reporting.service.ReportingService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API70 — Reports: the catalogue, the runs and the answers. */
@RestController
@RequestMapping("/v1/reports")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping
    public List<ReportDefinitionDto> catalogue() {
        return reportingService.findCatalogue(TenantContext.require());
    }

    @GetMapping("/runs")
    public List<ReportRunDto> runs() {
        return reportingService.findRecentRuns(TenantContext.require());
    }

    /**
     * Running a report is a POST: it records an execution.
     *
     * <p>Without dates, the window is the definition's default, ending today.
     */
    @PostMapping("/{code}/run")
    public ReportResultDto run(@PathVariable String code,
                               @RequestParam(name = "from", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(name = "to", required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(name = "windowDays", required = false) Integer windowDays,
                               @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(windowDays == null ? 30 : windowDays);
        return reportingService.run(TenantContext.require(), code, start, end, actorId);
    }
}
