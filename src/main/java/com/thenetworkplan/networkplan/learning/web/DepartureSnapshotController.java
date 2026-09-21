package com.thenetworkplan.networkplan.learning.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.learning.dto.DepartureSnapshotDto;
import com.thenetworkplan.networkplan.learning.service.DepartureSnapshotService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /v1/learning/departure-snapshots?from=YYYY-MM-DD&to=YYYY-MM-DD — l'export
 * des photos a H-1, pour le script d'entrainement. Fin exclue ; par defaut les
 * trente derniers jours.
 */
@RestController
@RequestMapping("/v1/learning")
public class DepartureSnapshotController {

    private final DepartureSnapshotService service;

    public DepartureSnapshotController(DepartureSnapshotService service) {
        this.service = service;
    }

    @GetMapping("/departure-snapshots")
    public List<DepartureSnapshotDto> snapshots(
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate end = to != null ? to : today.plusDays(1);
        LocalDate start = from != null ? from : end.minusDays(30);
        return service.find(TenantContext.require(), start, end);
    }
}
