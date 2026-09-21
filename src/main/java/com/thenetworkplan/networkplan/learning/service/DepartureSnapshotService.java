package com.thenetworkplan.networkplan.learning.service;

import com.thenetworkplan.networkplan.learning.dto.DepartureSnapshotDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * La collecte d'apprentissage : photographier chaque depart une heure avant,
 * puis noter le resultat quand il est connu.
 */
public interface DepartureSnapshotService {

    /**
     * Photographie les etapes dont le depart programme entre dans la fenetre
     * H-1 a l'instant {@code now}, une seule fois chacune.
     *
     * @return le nombre de photos prises
     */
    int takeDue(UUID tenantId, OffsetDateTime now);

    /**
     * Complete les photos en attente dont l'etape est partie ou annulee.
     *
     * @return le nombre de photos completees
     */
    int settleOutcomes(UUID tenantId, OffsetDateTime now);

    /** Les photos des departs programmes entre deux dates (bornes UTC, fin exclue). */
    List<DepartureSnapshotDto> find(UUID tenantId, LocalDate from, LocalDate to);
}
