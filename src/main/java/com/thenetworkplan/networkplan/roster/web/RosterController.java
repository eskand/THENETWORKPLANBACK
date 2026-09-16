package com.thenetworkplan.networkplan.roster.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.roster.dto.CreateRosterVersionCommand;
import com.thenetworkplan.networkplan.roster.dto.RosterCellDto;
import com.thenetworkplan.networkplan.roster.dto.RosterGridDto;
import com.thenetworkplan.networkplan.roster.dto.RosterMonthDto;
import com.thenetworkplan.networkplan.roster.dto.RosterVersionDto;
import com.thenetworkplan.networkplan.roster.dto.SaveRosterEntryCommand;
import com.thenetworkplan.networkplan.roster.service.RosterService;
import jakarta.validation.Valid;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
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
 * API22 — Roster.
 *
 * <p>The actor comes from an {@code X-Actor-Id} header while authentication is
 * not wired, exactly as on {@code LegController}: publishing a roster is signed,
 * and the signature must be a real user id from day one.
 */
@RestController
@RequestMapping("/v1/roster")
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping("/versions")
    public List<RosterVersionDto> versions() {
        return rosterService.findVersions(TenantContext.require());
    }

    /** Without {@code versionId}, the roster published for today. */
    @GetMapping("/grid")
    public RosterGridDto grid(@RequestParam(name = "versionId", required = false) UUID versionId) {
        return rosterService.findGrid(TenantContext.require(), versionId);
    }

    /**
     * The month the prototype navigates: « September 2026 ».
     *
     * <p>Without {@code month}, the month containing today — the one a planner
     * opens the screen to look at.
     */
    @GetMapping("/month")
    public RosterMonthDto month(@RequestParam(name = "month", required = false) String month) {
        YearMonth target = month == null || month.isBlank()
                ? YearMonth.now(ZoneOffset.UTC)
                : YearMonth.parse(month);
        return rosterService.findMonth(TenantContext.require(), target);
    }

    @PostMapping("/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public RosterVersionDto create(@Valid @RequestBody CreateRosterVersionCommand command) {
        return rosterService.create(TenantContext.require(), command);
    }

    @PostMapping("/versions/{id}/publish")
    public RosterVersionDto publish(@PathVariable UUID id,
                                    @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return rosterService.publish(TenantContext.require(), id, actorId);
    }

    @PutMapping("/versions/{id}/entries")
    public RosterCellDto saveEntry(@PathVariable UUID id,
                                   @Valid @RequestBody SaveRosterEntryCommand command) {
        return rosterService.saveEntry(TenantContext.require(), id, command);
    }

    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeEntry(@PathVariable UUID id) {
        rosterService.removeEntry(TenantContext.require(), id);
    }
}
