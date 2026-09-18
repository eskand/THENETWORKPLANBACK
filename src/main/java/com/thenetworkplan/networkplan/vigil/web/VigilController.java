package com.thenetworkplan.networkplan.vigil.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.AskVigilCommand;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.SetVigilAlertStatusCommand;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAlertDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAnswerDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilPanelDto;
import com.thenetworkplan.networkplan.vigil.service.VigilService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le panneau VIGIL — {@code window.TNPVIGIL} de l'annexe (l. 99432), en trois
 * routes : lire (ce qui balaye), changer l'etat d'une alerte, poser une
 * question.
 */
@RestController
@RequestMapping("/v1/vigil")
public class VigilController {

    private final VigilService vigilService;

    public VigilController(VigilService vigilService) {
        this.vigilService = vigilService;
    }

    /** Balaye et rend le panneau — {@code TNPVIGIL.scan()} puis {@code UI.refresh()}. */
    @GetMapping("/panel")
    public VigilPanelDto panel() {
        return vigilService.panel(TenantContext.require());
    }

    /** Acknowledge · In progress · Resolve · Dismiss — {@code ALERTS.setStatus()}. */
    @PostMapping("/alerts/{id}/status")
    public VigilAlertDto setStatus(@PathVariable UUID id,
                                   @Valid @RequestBody SetVigilAlertStatusCommand command,
                                   @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return vigilService.setStatus(TenantContext.require(), id, command.status(), actorId);
    }

    /** « Ask VIGIL » — {@code TNPVIGIL.ask(q)}. */
    @PostMapping("/ask")
    public VigilAnswerDto ask(@Valid @RequestBody AskVigilCommand command) {
        return vigilService.ask(TenantContext.require(), command.question());
    }
}
