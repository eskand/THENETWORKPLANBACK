package com.thenetworkplan.networkplan.crew.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.crew.dto.AbsenceDto;
import com.thenetworkplan.networkplan.crew.dto.OpsQualificationDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDetailDto;
import com.thenetworkplan.networkplan.crew.dto.PersonDto;
import com.thenetworkplan.networkplan.crew.dto.CrewExpiryDto;
import com.thenetworkplan.networkplan.crew.dto.QualificationDto;
import com.thenetworkplan.networkplan.crew.dto.SaveAbsenceCommand;
import com.thenetworkplan.networkplan.crew.dto.SaveApproachCategoryCommand;
import com.thenetworkplan.networkplan.crew.dto.SavePersonCommand;
import com.thenetworkplan.networkplan.crew.dto.SaveQualificationCommand;
import com.thenetworkplan.networkplan.crew.service.CrewPeopleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API20 — Crew Management: the crew files, their qualifications and absences. */
@RestController
@RequestMapping("/v1/crew")
public class CrewPeopleController {

    private final CrewPeopleService crewPeopleService;

    public CrewPeopleController(CrewPeopleService crewPeopleService) {
        this.crewPeopleService = crewPeopleService;
    }

    @GetMapping("/persons")
    public List<PersonDto> list(@RequestParam(name = "role", required = false) String role,
                                @RequestParam(name = "search", required = false) String search,
                                @RequestParam(name = "activeOnly", defaultValue = "true") boolean activeOnly) {
        return crewPeopleService.findAll(TenantContext.require(), role, search, activeOnly);
    }

    @GetMapping("/persons/{id}")
    public PersonDetailDto detail(@PathVariable UUID id) {
        return crewPeopleService.findOne(TenantContext.require(), id);
    }

    @PostMapping("/persons")
    @ResponseStatus(HttpStatus.CREATED)
    public PersonDto create(@Valid @RequestBody SavePersonCommand command) {
        return crewPeopleService.create(TenantContext.require(), command);
    }

    @PutMapping("/persons/{id}")
    public PersonDto update(@PathVariable UUID id, @Valid @RequestBody SavePersonCommand command) {
        return crewPeopleService.update(TenantContext.require(), id, command);
    }

    /**
     * The expiry wall of the whole crew.
     *
     * <p>Ninety days by default: long enough to book a recurrent course, short
     * enough that the list stays actionable.
     */
    @GetMapping("/expiries")
    public List<CrewExpiryDto> expiries(@RequestParam(name = "horizonDays", defaultValue = "90") int horizonDays) {
        return crewPeopleService.findExpiring(TenantContext.require(), horizonDays);
    }

    @PostMapping("/persons/{id}/qualifications")
    @ResponseStatus(HttpStatus.CREATED)
    public QualificationDto addQualification(@PathVariable UUID id,
                                             @Valid @RequestBody SaveQualificationCommand command) {
        return crewPeopleService.addQualification(TenantContext.require(), id, command);
    }

    /** Settings → OPS Qualifications: the flight crew and their approach category. */
    @GetMapping("/ops-qualifications")
    public List<OpsQualificationDto> opsQualifications() {
        return crewPeopleService.findOpsQualifications(TenantContext.require());
    }

    @PutMapping("/persons/{id}/approach-category")
    public OpsQualificationDto setApproachCategory(@PathVariable UUID id,
                                                   @Valid @RequestBody SaveApproachCategoryCommand command) {
        return crewPeopleService.setApproachCategory(TenantContext.require(), id, command);
    }

    @DeleteMapping("/qualifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeQualification(@PathVariable UUID id) {
        crewPeopleService.removeQualification(TenantContext.require(), id);
    }

    @PostMapping("/persons/{id}/absences")
    @ResponseStatus(HttpStatus.CREATED)
    public AbsenceDto addAbsence(@PathVariable UUID id, @Valid @RequestBody SaveAbsenceCommand command) {
        return crewPeopleService.addAbsence(TenantContext.require(), id, command);
    }

    @DeleteMapping("/absences/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAbsence(@PathVariable UUID id) {
        crewPeopleService.removeAbsence(TenantContext.require(), id);
    }
}
