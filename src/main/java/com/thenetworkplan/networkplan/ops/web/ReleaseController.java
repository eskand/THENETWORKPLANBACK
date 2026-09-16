package com.thenetworkplan.networkplan.ops.web;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.ops.dto.AcknowledgeReleaseCommand;
import com.thenetworkplan.networkplan.ops.dto.ReleaseDto;
import com.thenetworkplan.networkplan.ops.dto.SignReleaseCommand;
import com.thenetworkplan.networkplan.ops.service.ReleaseService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API13 — dispatch release and commander acknowledgement. */
@RestController
@RequestMapping("/v1/legs/{legId}/release")
public class ReleaseController {

    private final ReleaseService releaseService;

    public ReleaseController(ReleaseService releaseService) {
        this.releaseService = releaseService;
    }

    @GetMapping
    public ReleaseDto current(@PathVariable UUID legId) {
        return releaseService.findCurrent(TenantContext.require(), legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Release for leg", legId));
    }

    @PostMapping
    public ReleaseDto sign(@PathVariable UUID legId, @RequestBody SignReleaseCommand command) {
        return releaseService.sign(TenantContext.require(), legId, command);
    }

    @PostMapping("/acknowledge")
    public ReleaseDto acknowledge(@PathVariable UUID legId,
                                  @Valid @RequestBody AcknowledgeReleaseCommand command) {
        return releaseService.acknowledge(TenantContext.require(), legId, command);
    }
}
