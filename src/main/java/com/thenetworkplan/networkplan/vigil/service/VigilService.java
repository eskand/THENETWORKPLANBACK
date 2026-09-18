package com.thenetworkplan.networkplan.vigil.service;

import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAlertDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilAnswerDto;
import com.thenetworkplan.networkplan.vigil.dto.VigilDtos.VigilPanelDto;
import java.util.UUID;

/**
 * VIGIL — la couche d'intelligence operationnelle continue de l'annexe
 * (prototype l. 97841-99460), portee la ou elle disait elle-meme vouloir
 * aller : « The moment NetPlus gains a server, VIGIL.core.scan() and
 * VIGIL.reports.generate() are the two entry points to move server-side. »
 *
 * <p>Gouvernance inchangee : VIGIL detecte, analyse, priorise, recommande. Il
 * ne change JAMAIS un appareil, une route, un equipage ou une release. Chaque
 * reponse separe FACT / PREDICTION / RECOMMENDATION.
 */
public interface VigilService {

    /**
     * Balaye le programme du jour, met a jour les alertes et rend le panneau.
     *
     * <p>Le balayage est fait a la lecture : c'est le panneau qui donne la
     * cadence, comme le Web Worker de l'annexe donnait la sienne.
     */
    VigilPanelDto panel(UUID tenantId);

    VigilAlertDto setStatus(UUID tenantId, UUID alertId, String status, UUID actorId);

    /** « Ask VIGIL » — l'intelligence locale de l'annexe ({@code askLocal}, l. 98945). */
    VigilAnswerDto ask(UUID tenantId, String question);
}
