package com.thenetworkplan.networkplan.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Crew parameters that belong to the operator.
 *
 * <p>{@code minimumRest} is an <em>operator</em> threshold, not the regulation:
 * ORO.FTL.235 makes the minimum rest depend on the preceding duty and on the
 * base, and that computation belongs to the FTL engine of sprint S7. Until it
 * exists, scheduling flags a short rest against this single declared value and
 * says so — rather than pretending a legality check has been made.
 */
@Component
@ConfigurationProperties(prefix = "netplus.crew")
@Getter
@Setter
public class CrewProperties {

    /** Rest below which the scheduling board raises a warning. */
    private Duration minimumRest = Duration.ofHours(12);

    /** Reporting time before STD used when a duty period has to be proposed. */
    private Duration reportBeforeStd = Duration.ofMinutes(75);

    /** Off-duty time after STA. */
    private Duration offDutyAfterSta = Duration.ofMinutes(30);
}
