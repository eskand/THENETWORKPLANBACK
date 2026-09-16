package com.thenetworkplan.networkplan.crewscheduling.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.crew.dto.CrewMemberDto;
import com.thenetworkplan.networkplan.crewscheduling.dto.AssignSeatCommand;
import com.thenetworkplan.networkplan.crewscheduling.dto.SchedulingBoardDto;
import com.thenetworkplan.networkplan.crewscheduling.service.CrewSchedulingService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API23 — Crew Scheduling: the day's seats and the pool that can fill them. */
@RestController
@RequestMapping("/v1/crew/scheduling")
public class CrewSchedulingController {

    private final CrewSchedulingService crewSchedulingService;

    public CrewSchedulingController(CrewSchedulingService crewSchedulingService) {
        this.crewSchedulingService = crewSchedulingService;
    }

    @GetMapping("/board")
    public SchedulingBoardDto board(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "role", required = false) String role) {
        LocalDate day = date != null ? date : LocalDate.now(ZoneOffset.UTC);
        return crewSchedulingService.findBoard(TenantContext.require(), day, role);
    }

    @PostMapping("/legs/{legId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public CrewMemberDto assign(@PathVariable UUID legId,
                                @Valid @RequestBody AssignSeatCommand command,
                                @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return crewSchedulingService.assign(TenantContext.require(), legId, command, actorId);
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassign(@PathVariable UUID id) {
        crewSchedulingService.unassign(TenantContext.require(), id);
    }
}
