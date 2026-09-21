package com.thenetworkplan.networkplan.learning.service.impl;

import com.thenetworkplan.networkplan.learning.service.DepartureSnapshotService;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Le balayage periodique : photographier les departs qui entrent dans la
 * fenetre H-1, completer ceux qui sont partis. Tenant par tenant, et une
 * erreur chez l'un n'arrete pas les autres.
 *
 * <p>Les tenants sont ceux qui ont un depart programme dans les vingt-quatre
 * heures autour de l'instant : un tenant sans vol n'a rien a photographier.
 */
@Component
@ConditionalOnProperty(prefix = "netplus.learning", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DepartureSnapshotScheduler {

    private static final Logger log = LoggerFactory.getLogger(DepartureSnapshotScheduler.class);

    private final DepartureSnapshotService service;
    private final LegRepository legRepository;

    public DepartureSnapshotScheduler(DepartureSnapshotService service, LegRepository legRepository) {
        this.service = service;
        this.legRepository = legRepository;
    }

    @Scheduled(fixedDelayString = "${netplus.learning.period:PT5M}", initialDelayString = "PT1M")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (UUID tenantId : legRepository.findTenantsWithDepartures(now.minusHours(24), now.plusHours(24))) {
            try {
                int taken = service.takeDue(tenantId, now);
                int settled = service.settleOutcomes(tenantId, now);
                if (taken > 0 || settled > 0) {
                    log.info("departure snapshots — tenant {} : {} taken, {} settled", tenantId, taken, settled);
                }
            } catch (RuntimeException ex) {
                log.warn("departure snapshots — tenant {} failed: {}", tenantId, ex.getMessage());
            }
        }
    }
}
