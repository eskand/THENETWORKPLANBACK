package com.thenetworkplan.networkplan.crew.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.crew.service.CrewAssignmentService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API10 — FTL verdict and document state of the crew assigned to a leg. */
@RestController
@RequestMapping("/v1/legs/{legId}")
public class CrewController {

    private final CrewAssignmentService crewAssignmentService;

    public CrewController(CrewAssignmentService crewAssignmentService) {
        this.crewAssignmentService = crewAssignmentService;
    }

    @GetMapping("/crew-legality")
    public LegCrewDto crewLegality(
            @PathVariable UUID legId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate flightDate = date != null ? date : LocalDate.now();
        return crewAssignmentService.findByLeg(TenantContext.require(), legId, flightDate);
    }
}
