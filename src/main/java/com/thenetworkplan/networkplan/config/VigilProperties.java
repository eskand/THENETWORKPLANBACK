package com.thenetworkplan.networkplan.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Les seuils de VIGIL — des reglages d'exploitant, pas des limites
 * reglementaires.
 *
 * <p>Ce sont les valeurs de {@code DEFAULT_CFG} de l'annexe (prototype
 * l. 97952), a l'unite pres : les heures avant le depart en deca desquelles un
 * permis, un service ou un equipage manquant passe de « warning » a « high »
 * puis a « critical ». L'annexe les tenait dans localStorage ; ici elles sont
 * declarees, donc lisibles et modifiables sans toucher au code.
 */
@Component
@ConfigurationProperties(prefix = "netplus.vigil")
@Getter
@Setter
public class VigilProperties {

    /** Au-dela, une etape n'est pas encore surveillee (heures avant le depart). */
    private double horizonHours = 24;

    private double permitCriticalHours = 3;
    private double permitWarnHours = 8;

    private double serviceCriticalHours = 2.5;
    private double serviceWarnHours = 6;

    private double crewCriticalHours = 2;
    private double crewWarnHours = 6;

    /** Fenetre du pronostic de capacite flotte, en heures. */
    private int capacityLookaheadHours = 18;

    /** Cadence a laquelle le panneau se rafraichit — {@code scanEveryS}. */
    private int scanEverySeconds = 60;
}
