package com.thenetworkplan.networkplan.training.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.training.dto.EnrolCommand;
import com.thenetworkplan.networkplan.training.dto.PersonTrainingDto;
import com.thenetworkplan.networkplan.training.dto.RecordCompletionCommand;
import com.thenetworkplan.networkplan.training.dto.SaveSessionCommand;
import com.thenetworkplan.networkplan.training.dto.TrainingComplianceDto;
import com.thenetworkplan.networkplan.training.dto.TrainingCourseDto;
import com.thenetworkplan.networkplan.training.dto.TrainingEnrolmentDto;
import com.thenetworkplan.networkplan.training.dto.TrainingRecordDto;
import com.thenetworkplan.networkplan.training.dto.TrainingSessionDto;
import com.thenetworkplan.networkplan.training.dto.UpdateEnrolmentCommand;
import com.thenetworkplan.networkplan.training.service.TrainingService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API21 — Training: catalogue, calendar, files and compliance. */
@RestController
@RequestMapping("/v1/training")
public class TrainingController {

    private final TrainingService trainingService;

    public TrainingController(TrainingService trainingService) {
        this.trainingService = trainingService;
    }

    @GetMapping("/courses")
    public List<TrainingCourseDto> courses() {
        return trainingService.findCourses(TenantContext.require());
    }

    /** Defaults to the coming ninety days: the horizon a training plan is booked on. */
    @GetMapping("/sessions")
    public List<TrainingSessionDto> sessions(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to != null ? to : start.plusDays(90);
        return trainingService.findSessions(TenantContext.require(), start, end);
    }

    @GetMapping("/compliance")
    public TrainingComplianceDto compliance() {
        return trainingService.findCompliance(TenantContext.require());
    }

    @GetMapping("/persons/{id}")
    public PersonTrainingDto personFile(@PathVariable UUID id) {
        return trainingService.findPersonFile(TenantContext.require(), id);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingSessionDto createSession(@Valid @RequestBody SaveSessionCommand command) {
        return trainingService.createSession(TenantContext.require(), command);
    }

    @PostMapping("/sessions/{id}/enrolments")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingEnrolmentDto enrol(@PathVariable UUID id, @Valid @RequestBody EnrolCommand command) {
        return trainingService.enrol(TenantContext.require(), id, command);
    }

    @PatchMapping("/enrolments/{id}")
    public TrainingEnrolmentDto updateEnrolment(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateEnrolmentCommand command) {
        return trainingService.updateEnrolment(TenantContext.require(), id, command);
    }

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingRecordDto recordCompletion(@Valid @RequestBody RecordCompletionCommand command) {
        return trainingService.recordCompletion(TenantContext.require(), command);
    }
}
