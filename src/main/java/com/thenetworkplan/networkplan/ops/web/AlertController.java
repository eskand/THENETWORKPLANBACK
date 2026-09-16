package com.thenetworkplan.networkplan.ops.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.ops.dto.AlertDto;
import com.thenetworkplan.networkplan.ops.service.AlertService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR15 / FR40 — the alert wall. */
@RestController
@RequestMapping("/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertDto> open() {
        return alertService.findOpen(TenantContext.require());
    }

    @PostMapping("/{id}/acknowledge")
    public AlertDto acknowledge(@PathVariable UUID id,
                                @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return alertService.acknowledge(TenantContext.require(), id, actorId);
    }
}
