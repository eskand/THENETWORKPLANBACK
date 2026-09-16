package com.thenetworkplan.networkplan.safety.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterCommands.AnswerQueryCommand;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterCommands.AskQueryCommand;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.HazardRegisterDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.NotificationCentreDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.QueryDto;
import com.thenetworkplan.networkplan.safety.dto.SmsRegisterDtos.RexDto;
import com.thenetworkplan.networkplan.safety.service.SmsRegisterService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The hazard register, the REX library, the queries and the notifications. */
@RestController
@RequestMapping("/v1/safety")
public class SmsRegisterController {

    private final SmsRegisterService registerService;

    public SmsRegisterController(SmsRegisterService registerService) {
        this.registerService = registerService;
    }

    @GetMapping("/hazards")
    public HazardRegisterDto register() {
        return registerService.findRegister(TenantContext.require());
    }

    @PatchMapping("/hazards/{id}/review")
    public HazardRegisterDto review(@PathVariable UUID id,
                                    @RequestParam(defaultValue = "90") int cycleDays) {
        return registerService.recordReview(TenantContext.require(), id, cycleDays);
    }

    @GetMapping("/rex")
    public List<RexDto> rex(@RequestParam(required = false) String reader) {
        return registerService.findRexLibrary(TenantContext.require(), reader);
    }

    @PostMapping("/rex/{id}/read")
    public List<RexDto> readRex(@PathVariable UUID id, @RequestParam String reader) {
        return registerService.markRexRead(TenantContext.require(), id, reader);
    }

    @GetMapping("/queries")
    public List<QueryDto> queries(@RequestParam(required = false) String reporter,
                                  @RequestParam(defaultValue = "true") boolean openOnly) {
        return registerService.findQueries(TenantContext.require(), reporter, openOnly);
    }

    @PostMapping("/occurrences/{id}/query")
    public QueryDto ask(@PathVariable UUID id, @Valid @RequestBody AskQueryCommand command) {
        return registerService.askQuery(TenantContext.require(), id, command);
    }

    @PostMapping("/occurrences/{id}/query/answer")
    public QueryDto answer(@PathVariable UUID id, @Valid @RequestBody AnswerQueryCommand command) {
        return registerService.answerQuery(TenantContext.require(), id, command);
    }

    @GetMapping("/notifications")
    public NotificationCentreDto notifications() {
        return registerService.findNotifications(TenantContext.require());
    }

    @PatchMapping("/notifications/{id}/read")
    public NotificationCentreDto markRead(@PathVariable UUID id) {
        return registerService.markRead(TenantContext.require(), id);
    }

    @PatchMapping("/notifications/read-all")
    public NotificationCentreDto markAllRead() {
        return registerService.markAllRead(TenantContext.require());
    }
}
