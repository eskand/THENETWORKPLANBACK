package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Une etape de la frise OCC, de la creation du vol a sa cloture.
 *
 * <p>C'est le {@code events[]} de {@code computeOccTimeline()} de l'annexe
 * (l. 13824), a ceci pres que chaque fenetre est une heure reelle et non un
 * decimal d'heure locale au navigateur.
 *
 * @param key    identifiant stable de l'evenement, pour l'interface et les tests
 * @param label  son nom, dans les mots de l'annexe
 * @param start  debut de la fenetre operationnelle
 * @param end    fin de la fenetre ; egal a {@code start} pour un evenement ponctuel
 * @param status PENDING, SCHEDULED, IN_PROGRESS, COMPLETED, DELAYED ou CANCELLED
 * @param note   la ligne explicative, quand il y en a une ; jamais du remplissage
 * @param action CONFIRM_RELEASE, SET_SLOT ou {@code null} — le bouton que la
 *               ligne porte, decide ici et non dans l'interface, parce que c'est
 *               l'etat serveur qui dit si le geste a encore un sens
 */
public record OccEventDto(
        String key,
        String label,
        OffsetDateTime start,
        OffsetDateTime end,
        String status,
        String note,
        String action) implements Serializable {
}
