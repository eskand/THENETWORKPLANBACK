package com.thenetworkplan.networkplan.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * La collecte d'apprentissage : la « photo a H-1 » de chaque depart.
 *
 * <ul>
 *   <li>{@code lead} — l'horizon de prediction : la photo est prise quand le
 *       depart programme est a cette distance. Une heure, comme le script
 *       d'entrainement ({@code --lead 60}).
 *   <li>{@code window} — la largeur de la fenetre de capture : une etape dont
 *       le STD tombe dans ]now + lead - window, now + lead] est photographiee.
 *       Doit couvrir au moins une periode du planificateur, sinon des departs
 *       passent entre deux balayages.
 *   <li>{@code period} — la cadence du planificateur.
 *   <li>{@code weatherStaleAfter} — un METAR plus vieux que cela a l'instant
 *       de la photo n'est pas retenu : l'absence est une information.
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "netplus.learning")
@Getter
@Setter
public class LearningProperties {

    private boolean enabled = true;

    private Duration lead = Duration.ofMinutes(60);

    private Duration window = Duration.ofMinutes(10);

    private Duration period = Duration.ofMinutes(5);

    private Duration weatherStaleAfter = Duration.ofHours(2);

    /** Les mouvements programmes comptes a +/- cette marge pour la congestion. */
    private Duration congestionWindow = Duration.ofMinutes(30);
}
