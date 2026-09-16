package com.thenetworkplan.networkplan.permits.service.impl;

import com.thenetworkplan.networkplan.permits.repository.NavChargeZoneRepository;
import com.thenetworkplan.networkplan.permits.service.NavChargeProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ASECNA — Redevance d'usage des aides et services en route, effective
 * 1 January 2026.
 *
 * <p>The scheme that justifies the whole provider architecture: ASECNA bills
 * <b>once</b>, on the total distance flown across all its FIRs combined, not
 * per FIR. Applying EUROCONTROL's per-zone loop here would bill a Dakar–Niamey
 * sector several times over.
 *
 * <pre>
 *   MTOW &lt; 4 t                exempt
 *   4 t ≤ MTOW ≤ 14 t          flat 231.80 EUR (international)
 *   MTOW &gt; 14 t               coefficient(weight band, distance band) × unit
 *
 *   unit: 75.34 national · 92.73 regional · 115.90 international (EUR)
 *   distance bands: ≤750 km · ≤2000 km · ≤3500 km · beyond
 * </pre>
 *
 * <p>The weight bands and coefficients are the published table, transcribed
 * without rounding or interpolation. A weight above the last band uses the last
 * band, which is what the tariff says to do — not an extrapolation of our own.
 */
@Component
public class AsecnaChargeProvider implements NavChargeProvider {

    private static final double KM_PER_NM = 1.852;

    private static final String SOURCE =
            "ASECNA — Redevance d'usage des aides et services en route, eff. 1 Jan 2026";

    /** Upper bound of the weight band in tonnes, then one coefficient per distance band. */
    private static final double[][] WEIGHT_BANDS = {
            { 20, 1.00, 5.00, 12.00, 20.00 },
            { 50, 1.20, 6.00, 14.40, 24.00 },
            { 90, 1.40, 7.00, 16.80, 28.00 },
            { 140, 1.60, 8.00, 19.20, 32.00 },
            { 200, 1.80, 9.00, 21.60, 36.00 },
            { 270, 2.00, 10.00, 24.00, 40.00 },
            { 350, 2.15, 10.75, 25.80, 43.00 },
            { 440, 2.30, 11.50, 27.60, 46.00 },
            { 540, 2.45, 12.25, 29.40, 49.00 },
            { 650, 2.60, 13.00, 31.20, 52.00 },
    };

    private final NavChargeZoneRepository zones;

    public AsecnaChargeProvider(NavChargeZoneRepository zones) {
        this.zones = zones;
    }

    @Override
    public String id() {
        return "ASECNA";
    }

    @Override
    public String label() {
        return "ASECNA";
    }

    @Override
    public boolean coversFir(String firCode) {
        return zones.isProviderFir(id(), firCode);
    }

    @Override
    public Charge compute(Map<String, Double> distanceNmByFir, Integer mtowKg, String flightNature) {
        if (mtowKg == null || mtowKg <= 0) {
            return Charge.notImplemented("MTOW is required by the ASECNA weight bands");
        }

        double totalKm = 0;
        for (Map.Entry<String, Double> entry : distanceNmByFir.entrySet()) {
            if (coversFir(entry.getKey())) {
                totalKm += entry.getValue() * KM_PER_NM;
            }
        }
        if (totalKm <= 0) {
            return Charge.notImplemented("No ASECNA FIR on this route");
        }

        double tonnes = mtowKg / 1000.0;
        int distanceKm = (int) Math.round(totalKm);
        String effective = "2026-01-01 → ";

        if (tonnes < 4) {
            return new Charge(true, BigDecimal.ZERO, null,
                    List.of(new Line(null, null, "ASECNA", distanceKm, BigDecimal.ZERO,
                            "Exempt below 4 t")),
                    SOURCE, effective);
        }
        if (tonnes <= 14) {
            BigDecimal flat = new BigDecimal("231.80");
            return new Charge(true, flat, null,
                    List.of(new Line(null, null, "ASECNA", distanceKm, flat,
                            "Flat rate 4–14 t, international")),
                    SOURCE, effective);
        }

        double unit = "national".equals(flightNature) ? 75.34
                : "regional".equals(flightNature) ? 92.73
                : 115.90;
        int distanceBand = totalKm <= 750 ? 0 : totalKm <= 2000 ? 1 : totalKm <= 3500 ? 2 : 3;

        double[] band = WEIGHT_BANDS[WEIGHT_BANDS.length - 1];
        for (double[] candidate : WEIGHT_BANDS) {
            if (tonnes <= candidate[0]) {
                band = candidate;
                break;
            }
        }
        double coefficient = band[1 + distanceBand];
        BigDecimal amount = BigDecimal.valueOf(coefficient * unit).setScale(2, RoundingMode.HALF_UP);

        return new Charge(true, amount, null,
                List.of(new Line(null, null, "ASECNA", distanceKm, amount,
                        "Coefficient " + coefficient + " × unit " + unit + " EUR ("
                                + (flightNature == null ? "international" : flightNature) + ")")),
                SOURCE, effective);
    }
}
