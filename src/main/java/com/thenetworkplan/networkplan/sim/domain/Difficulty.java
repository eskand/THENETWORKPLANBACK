package com.thenetworkplan.networkplan.sim.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The five difficulty presets of the Simulation Center.
 *
 * <p>Counts are the prototype's ({@code PRESETS}, annexe A4 l. 89842-89849) —
 * they are a training curriculum, not an arbitrary scale: EASY is one dispatcher
 * spotting waste, EXTREME is a full OCC day with aircraft on the ground,
 * weather, closed aerodromes and crew out of hours at the same time.
 *
 * <p>{@link #CUSTOM} carries no counts. It is the hand-built scenario, where
 * every injector is set one by one.
 */
public enum Difficulty {

    EASY("2 unnecessary ferries · 1 mispositioned aircraft · 1 under-utilised crew "
            + "· 1 minor rotation inefficiency",
            Map.of("ferry", 2, "mispos", 1, "underCrew", 1, "rotIneff", 1)),

    MEDIUM("5 ferries · 3 delays · 2 mispositioned · 2 crew imbalances "
            + "· 2 inefficient rotations · 1 maintenance approaching",
            Map.of("ferry", 5, "delay", 3, "mispos", 2, "underCrew", 1, "overCrew", 1,
                    "rotIneff", 2, "mxCrit", 1)),

    HARD("10 ferries · 5 delays · 5 cancellations · 5 mispositioned · 5 crew conflicts "
            + "· 3 critical MX · 2 AOG · repositionings",
            Map.of("ferry", 10, "delay", 5, "cxl", 5, "mispos", 5, "crewConf", 5,
                    "mxCrit", 3, "aog", 2, "repos", 3)),

    EXTREME("Full complex OCC environment: multiple AOG, weather, airport & runway closures, "
            + "parking limits, fleet imbalance, dormant tails, VIP requests",
            extreme()),

    CUSTOM("Define every anomaly count manually — one control per injector", Map.of());

    private final String description;
    private final Map<String, Integer> counts;

    Difficulty(String description, Map<String, Integer> counts) {
        this.description = description;
        this.counts = counts;
    }

    /* Too many entries for Map.of, which stops at ten pairs. */
    private static Map<String, Integer> extreme() {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("ferry", 12);
        map.put("delay", 8);
        map.put("cxl", 6);
        map.put("aog", 3);
        map.put("vip", 5);
        map.put("mispos", 5);
        map.put("underAC", 2);
        map.put("overAC", 2);
        map.put("rotIneff", 5);
        map.put("repos", 5);
        map.put("crewConf", 5);
        map.put("underCrew", 2);
        map.put("overCrew", 2);
        map.put("mxCrit", 3);
        map.put("camo", 3);
        map.put("oppty", 5);
        map.put("wx", 1);
        map.put("aptClosure", 1);
        map.put("rwyClosure", 1);
        map.put("parkingLimit", 1);
        map.put("crewRepos", 2);
        map.put("dormant", 2);
        map.put("farDemand", 1);
        map.put("cluster", 1);
        return map;
    }

    public String description() {
        return description;
    }

    /** What the preset asks for, by injector key. */
    public Map<String, Integer> counts() {
        return counts;
    }

    /**
     * How many anomalies this preset can actually place today.
     *
     * <p>Shown beside the total it asks for, so the card says "9 of 16" rather
     * than promising sixteen and quietly delivering nine.
     */
    public int implementedCount() {
        return counts.entrySet().stream()
                .filter(entry -> Injector.byKey(entry.getKey())
                        .map(Injector::isImplemented).orElse(false))
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public int requestedCount() {
        return counts.values().stream().mapToInt(Integer::intValue).sum();
    }
}
