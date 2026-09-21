package com.thenetworkplan.networkplan.ops.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.ops.dto.CancelLegCommand;
import com.thenetworkplan.networkplan.ops.dto.ChangeAircraftCommand;
import com.thenetworkplan.networkplan.ops.dto.CreateLegCommand;
import com.thenetworkplan.networkplan.ops.dto.DelayCodeDto;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.dto.MoveLegCommand;
import com.thenetworkplan.networkplan.ops.dto.OccTimelineDto;
import com.thenetworkplan.networkplan.ops.dto.ReadinessDto;
import com.thenetworkplan.networkplan.ops.dto.RecordMovementCommand;
import com.thenetworkplan.networkplan.ops.dto.SetSlotCommand;
import com.thenetworkplan.networkplan.ops.service.LegMovementService;
import com.thenetworkplan.networkplan.ops.service.LegReadinessService;
import com.thenetworkplan.networkplan.ops.service.LegService;
import com.thenetworkplan.networkplan.ops.service.OccTimelineService;
import jakarta.validation.Valid;
import java.time.LocalDate;
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

/**
 * API1 / API2 / API3 / API13 — the leg and its commands.
 *
 * <p>The actor comes from an {@code X-Actor-Id} header while authentication is
 * off. It is threaded through every command so the leg history is already
 * complete: when OIDC lands, the header is replaced by the token subject and
 * nothing below the controller changes.
 */
@RestController
@RequestMapping("/v1/legs")
public class LegController {

    private final LegService legService;
    private final LegReadinessService readinessService;
    private final LegMovementService movementService;
    private final OccTimelineService occTimelineService;

    public LegController(LegService legService,
                         LegReadinessService readinessService,
                         LegMovementService movementService,
                         OccTimelineService occTimelineService) {
        this.legService = legService;
        this.readinessService = readinessService;
        this.movementService = movementService;
        this.occTimelineService = occTimelineService;
    }

    @GetMapping
    public List<LegDto> programme(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return legService.findProgramme(TenantContext.require(),
                date != null ? date : LocalDate.now());
    }

    /** La liste des codes de retard du tenant, pour le choix de la cause a la saisie de l'ATD. */
    @GetMapping("/delay-codes")
    public List<DelayCodeDto> delayCodes() {
        return legService.delayCodes(TenantContext.require());
    }

    @GetMapping("/{id}")
    public LegDto one(@PathVariable UUID id) {
        return legService.findById(TenantContext.require(), id);
    }

    @GetMapping("/{id}/readiness")
    public ReadinessDto readiness(@PathVariable UUID id) {
        return readinessService.assess(TenantContext.require(), id);
    }

    /** La frise « OCC Dispatch » du menu du dossier de vol. */
    @GetMapping("/{id}/occ-timeline")
    public OccTimelineDto occTimeline(@PathVariable UUID id) {
        return occTimelineService.timeline(TenantContext.require(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegDto create(@Valid @RequestBody CreateLegCommand command,
                         @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return legService.create(TenantContext.require(), command, actorId);
    }

    @PatchMapping("/{id}/schedule")
    public LegDto move(@PathVariable UUID id,
                       @Valid @RequestBody MoveLegCommand command,
                       @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return legService.move(TenantContext.require(), id, command, actorId);
    }

    @PatchMapping("/{id}/aircraft")
    public LegDto changeAircraft(@PathVariable UUID id,
                                 @Valid @RequestBody ChangeAircraftCommand command,
                                 @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return legService.changeAircraft(TenantContext.require(), id, command, actorId);
    }

    /**
     * Le creneau ATC — le bouton « Set CTOT / Slot ref » de la frise OCC.
     *
     * <p>{@code PATCH} et non {@code POST} : le creneau est un champ de l'etape,
     * pas un evenement qu'on empile. Le reposer le remplace.
     */
    @PatchMapping("/{id}/slot")
    public LegDto setSlot(@PathVariable UUID id,
                          @Valid @RequestBody SetSlotCommand command,
                          @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return legService.setSlot(TenantContext.require(), id, command, actorId);
    }

    @PatchMapping("/{id}/cancel")
    public LegDto cancel(@PathVariable UUID id,
                         @Valid @RequestBody CancelLegCommand command,
                         @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return legService.cancel(TenantContext.require(), id, command, actorId);
    }

    @PostMapping("/{id}/movements")
    public LegDto movement(@PathVariable UUID id,
                           @Valid @RequestBody RecordMovementCommand command,
                           @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return movementService.recordMovement(TenantContext.require(), id, command, actorId);
    }

    @PostMapping("/{id}/mvt")
    public LegDto sendMvt(@PathVariable UUID id,
                          @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return movementService.markMvtSent(TenantContext.require(), id, actorId);
    }

    @PostMapping("/{id}/close")
    public LegDto close(@PathVariable UUID id,
                        @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return movementService.close(TenantContext.require(), id, actorId);
    }
}
