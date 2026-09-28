package com.thenetworkplan.networkplan.flightfollowing.service.impl;

import com.thenetworkplan.networkplan.flightfollowing.service.AdsbIngestService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Le relevé ADS-B en tâche de fond, à la cadence de {@code netplus.adsb.refresh-after}.
 *
 * <p>Il était fait dans la requête du tableau Flight Following : un écran ouvert
 * payait l'appel au fournisseur presque à chaque relecture. Il est fait ici, pour
 * les seuls tenants dont l'écran a lu le tableau dans les deux dernières minutes —
 * un écran fermé ne dépense pas le quota. Une erreur chez l'un n'arrête pas les
 * autres, comme DepartureSnapshotScheduler.
 */
@Component
@ConditionalOnProperty(prefix = "netplus.adsb", name = "background", havingValue = "true", matchIfMissing = true)
public class AdsbIngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(AdsbIngestScheduler.class);

    /** Au-delà, l'écran est tenu pour fermé. */
    static final long WATCH_MINUTES = 2;

    private final AdsbIngestService service;

    public AdsbIngestScheduler(AdsbIngestService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${netplus.adsb.refresh-after:PT30S}", initialDelayString = "${netplus.adsb.refresh-after:PT30S}")
    public void sweep() {
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(WATCH_MINUTES);
        for (UUID tenantId : service.watchedSince(since)) {
            try {
                service.ingest(tenantId);
            } catch (RuntimeException ex) {
                log.warn("ADS-B ingest — tenant {} failed: {}", tenantId, ex.getMessage());
            }
        }
    }
}
