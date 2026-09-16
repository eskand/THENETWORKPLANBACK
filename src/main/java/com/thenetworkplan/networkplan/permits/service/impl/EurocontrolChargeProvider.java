package com.thenetworkplan.networkplan.permits.service.impl;

import com.thenetworkplan.networkplan.permits.repository.NavChargeZoneRepository;
import com.thenetworkplan.networkplan.permits.service.NavChargeProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * EUROCONTROL route charges, per the Conditions of Application of the Route
 * Charges System, Articles 2 to 7 (Doc N.21.60.02, November 2021).
 *
 * <pre>
 *   R  = Σ rᵢ                    total charge
 *   rᵢ = tᵢ × Nᵢ                 per charging zone
 *   Nᵢ = dᵢ × p                  service units
 *   dᵢ = great-circle km in the zone ÷ 100
 *   p  = √(MTOW in tonnes ÷ 50)  weight factor
 * </pre>
 *
 * <p><b>One deliberate deviation from the real bill, and it is disclosed.</b>
 * The official scheme deducts twenty kilometres per take-off and per landing
 * inside a charging zone. That deduction needs the exact FIR boundary crossing
 * points, which a great-circle sample does not give. It is therefore not
 * applied, and the figure is a slight <em>over</em>estimate — never an
 * underestimate. An operator planning cash is better served by a number that
 * cannot surprise them downwards. The prototype makes the same choice and says
 * so; this keeps the choice and puts the sentence on the screen.
 *
 * <p>The charging zone is the first two letters of the FIR's ICAO ident, which
 * holds for the large majority of European FIRs. The documented exceptions live
 * in {@code refdata.fir_charge_zone_overrides} rather than in this file: an
 * exception buried in a function is an exception nobody finds again.
 */
@Component
public class EurocontrolChargeProvider implements NavChargeProvider {

    private static final double KM_PER_NM = 1.852;

    private final NavChargeZoneRepository zones;

    public EurocontrolChargeProvider(NavChargeZoneRepository zones) {
        this.zones = zones;
    }

    @Override
    public String id() {
        return "EUROCONTROL";
    }

    @Override
    public String label() {
        return "EUROCONTROL CRCO";
    }

    @Override
    public boolean coversFir(String firCode) {
        return zones.findRate(id(), zoneOf(firCode)).isPresent();
    }

    @Override
    public Charge compute(Map<String, Double> distanceNmByFir, Integer mtowKg, String flightNature) {
        if (mtowKg == null || mtowKg <= 0) {
            return Charge.notImplemented("MTOW is required for the weight factor p = √(MTOW ÷ 50)");
        }

        double weightFactor = Math.sqrt((mtowKg / 1000.0) / 50.0);
        BigDecimal total = BigDecimal.ZERO;
        List<Line> lines = new ArrayList<>();
        String sourceRef = null;
        String effective = null;

        for (Map.Entry<String, Double> entry : distanceNmByFir.entrySet()) {
            String zone = zoneOf(entry.getKey());
            var rate = zones.findRate(id(), zone).orElse(null);
            if (rate == null || rate.getUnitRateEur() == null) {
                // Not an implemented zone, or a zone whose current rate has not
                // been sourced. Either way it is left out and named, not zeroed.
                continue;
            }

            double distanceKm = entry.getValue() * KM_PER_NM;
            double serviceUnits = (distanceKm / 100.0) * weightFactor;
            BigDecimal amount = rate.getUnitRateEur()
                    .multiply(BigDecimal.valueOf(serviceUnits))
                    .setScale(2, RoundingMode.HALF_UP);

            total = total.add(amount);
            lines.add(new Line(entry.getKey(), zone, rate.getCountryLabel(),
                    (int) Math.round(distanceKm), amount, null));
            sourceRef = rate.getSourceRef();
            effective = rate.getEffectiveFrom() + " → " + rate.getEffectiveTo();
        }

        if (lines.isEmpty()) {
            return Charge.notImplemented(
                    "No FIR on this route matched a EUROCONTROL charging zone with a published unit rate");
        }
        return new Charge(true, total, null, List.copyOf(lines), sourceRef, effective);
    }

    /** First two letters of the FIR ident, unless an override says otherwise. */
    private String zoneOf(String firCode) {
        if (firCode == null || firCode.length() < 2) {
            return null;
        }
        return zones.findZoneOverride(firCode).orElse(firCode.substring(0, 2));
    }
}
