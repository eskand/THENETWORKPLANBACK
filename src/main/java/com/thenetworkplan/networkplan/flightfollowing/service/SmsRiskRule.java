package com.thenetworkplan.networkplan.flightfollowing.service;

import java.io.Serializable;
import java.util.List;

/**
 * Proactive SMS risk assessment of one flight.
 *
 * <p>A five-by-five severity times likelihood matrix in the style of ICAO
 * Doc 9859, ported from the prototype's {@code computeRisk()} without changing
 * a threshold. Five factors are scored — weather against minima, NOTAM impact,
 * flight time limitations, aircraft performance and MEL, crew — the worst
 * severity is taken, the likelihood is scaled by how many factors are active
 * at once, and the product lands in one of four bands.
 *
 * <p>It sits on the server for the reason every other rule does: a risk level
 * decides whether someone picks up a phone. The prototype computed it in the
 * browser, which meant the number on the screen and the number in the Risk
 * Register could differ while both looked right.
 *
 * <p>Two departures from the prototype, both deliberate:
 *
 * <ul>
 *   <li>A factor whose source has not answered scores {@link Level#UNKNOWN}
 *       rather than zero. The prototype read a missing weather feed as
 *       "above minima", which is the most dangerous default there is.
 *   <li>The assessment carries the factors that produced it, so the screen can
 *       say <em>why</em> a flight is amber instead of only that it is.
 * </ul>
 */
public interface SmsRiskRule {

    /** The five factors, in the order the panel lists them. */
    enum Factor {
        WEATHER("Weather vs. minima"),
        NOTAM("NOTAM impact"),
        FTL("Flight Time Limitation (FTL)"),
        MEL("Aircraft performance / MEL"),
        CREW("Crew risk");

        private final String label;

        Factor(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * How bad one factor is, on the prototype's one-to-five severity scale.
     *
     * <p>{@link #UNKNOWN} is not a severity: it means the source did not
     * answer. It never lowers a risk, and the screen names the factor so the
     * gap is visible rather than averaged away.
     */
    enum Level {
        NONE(1),
        MINOR(2),
        MODERATE(3),
        MAJOR(4),
        SEVERE(5),
        UNKNOWN(0);

        private final int severity;

        Level(int severity) {
            this.severity = severity;
        }

        public int severity() {
            return severity;
        }

        public boolean active() {
            return this != NONE && this != UNKNOWN;
        }
    }

    /** One scored factor, with the sentence the panel shows. */
    record Scored(Factor factor, Level level, String detail) implements Serializable {
    }

    /**
     * @param level     LOW / MEDIUM / HIGH / CRITICAL, from the index
     * @param index     severity times likelihood, one to twenty-five
     * @param action    what the operator is expected to do at that level
     * @param unknown   factors whose source did not answer
     */
    record Assessment(
            String level,
            int severity,
            int likelihood,
            int index,
            String action,
            List<Scored> factors,
            List<Factor> unknown) implements Serializable {
    }

    Assessment assess(List<Scored> factors);
}
