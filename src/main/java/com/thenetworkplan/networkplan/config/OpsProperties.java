package com.thenetworkplan.networkplan.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Operational parameters that belong to the operator, not to the code.
 *
 * <p>The audit found settings that existed in the prototype's UI and were read
 * nowhere ({@code dispatch.contingency}, {@code dispatch.finalReserve}). Anything
 * declared here is used.
 */
@Component
@ConfigurationProperties(prefix = "netplus.ops")
@Getter
@Setter
public class OpsProperties {

    /**
     * Below this turnaround, a delay on one leg pushes the next rotation of the
     * same tail (FR38 cascade).
     */
    private Duration minimumTurnaround = Duration.ofMinutes(50);

    /** A leg is "delayed" once the revised departure exceeds the schedule by this. */
    private Duration delayThreshold = Duration.ofMinutes(15);

    /**
     * On-time performance the operator holds itself to, in percent.
     *
     * <p>Shown next to the measured figure so the number has something to be
     * read against. It is the operator's own target, not an industry average:
     * it is declared here and in {@code platform.settings}, and read by the
     * dispatch board.
     */
    private int otpTargetPercent = 95;

    /**
     * Block hours a tail is expected to fly in a day, used as the denominator
     * of fleet utilisation on the Flight Timeline.
     *
     * <p>Ten hours is the figure the audited prototype divided by
     * {@code totalBlockMin/60/(totalAc*10)}, written inline there. It is an
     * operator assumption about what a full day looks like, not a measurement,
     * so it is declared here where it can be changed and seen.
     */
    private int dailyBlockHourReference = 10;
}
