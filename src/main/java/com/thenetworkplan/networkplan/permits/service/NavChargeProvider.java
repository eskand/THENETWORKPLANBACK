package com.thenetworkplan.networkplan.permits.service;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * One en-route navigation charge authority, with its own published method.
 *
 * <p>There is an interface here rather than one shared formula because the
 * formulas genuinely differ, and pretending otherwise would make one of them
 * wrong. EUROCONTROL bills <em>per charging zone crossed</em>; ASECNA bills
 * <em>once</em> on the total distance flown across all its FIRs combined. A
 * single "distance times rate" would have quietly overcharged every West
 * African sector.
 *
 * <p>The other rule this interface exists to enforce: a provider that has no
 * published rate on file answers {@link Charge#notImplemented}, with the
 * reason. It never returns zero, and it never interpolates from a neighbour.
 * Morocco and Egypt are exactly that case — EUROCONTROL bills them under the
 * Customer Guide part C, but the supplied documents do not publish their
 * current unit rate.
 */
public interface NavChargeProvider {

    /** EUROCONTROL, ASECNA, … Stored on every computed line. */
    String id();

    String label();

    /** True when this provider is the billing authority for that FIR. */
    boolean coversFir(String firCode);

    /**
     * @param amountEur   null when {@code implemented} is false
     * @param reason      why it could not be computed, when it could not
     * @param lines       the detail, per zone or as a single global line
     * @param sourceRef   the document the figures come from
     * @param effective   the validity window of the rates used
     */
    record Charge(
            boolean implemented,
            BigDecimal amountEur,
            String reason,
            List<Line> lines,
            String sourceRef,
            String effective) implements Serializable {

        public static Charge notImplemented(String reason) {
            return new Charge(false, null, reason, List.of(), null, null);
        }
    }

    /** One billed element: a charging zone, or the single global line of a flat scheme. */
    record Line(
            String firCode,
            String zoneCode,
            String countryLabel,
            int distanceKm,
            BigDecimal amountEur,
            String note) implements Serializable {
    }

    /**
     * @param distanceNmByFir great-circle nautical miles flown inside each FIR
     * @param mtowKg          maximum take-off weight; the weight factor needs it
     * @param flightNature    national / regional / international — ASECNA prices differ
     */
    Charge compute(Map<String, Double> distanceNmByFir, Integer mtowKg, String flightNature);
}
