package com.thenetworkplan.networkplan.timeline.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.timeline.dto.TimelineDto;
import com.thenetworkplan.networkplan.timeline.service.TimelineService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API40 — Flight Timeline: rotations and ground time. */
@RestController
@RequestMapping("/v1/timeline")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    /**
     * The three selectors are optional and are filtered on the server.
     *
     * <p>Filtering in the browser would have been less code, and wrong: the
     * header tiles count the whole fleet while the lanes are narrowed, and a
     * screen that hides rows it has already downloaded lies about how much it
     * knows.
     */
    @GetMapping
    public TimelineDto timeline(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "days", defaultValue = "1") int days,
            @RequestParam(name = "includeIdle", defaultValue = "true") boolean includeIdle,
            @RequestParam(name = "fleet", required = false) String fleetSection,
            @RequestParam(name = "base", required = false) String baseIcao,
            @RequestParam(name = "status", required = false) String statusTone) {
        LocalDate start = from != null ? from : LocalDate.now(ZoneOffset.UTC);
        return timelineService.findTimeline(
                TenantContext.require(), start, days, includeIdle,
                blankToNull(fleetSection), blankToNull(baseIcao), blankToNull(statusTone));
    }

    /** "All" and the empty string both mean no restriction. */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || "All".equalsIgnoreCase(value)) {
            return null;
        }
        return value.trim();
    }
}
