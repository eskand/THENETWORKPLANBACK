package com.thenetworkplan.networkplan.sim.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The anomaly generators, one per kind of thing that can go wrong.
 *
 * <p><b>{@code implemented} is not decoration.</b> The prototype declares
 * twenty-four injectors; eleven of them are what the EASY, MEDIUM and HARD
 * presets are built from, and those are the eleven that write into a scenario
 * here. The rest are declared with their anomaly type so that the preset, the
 * custom builder and the API all know they exist — and every response says how
 * many of each were <em>requested</em> against how many were <em>applied</em>,
 * so an injector that does nothing cannot pass for one that did.
 *
 * <p>An implemented injector can also apply fewer than asked, legitimately:
 * an unnecessary ferry needs a ground gap long enough to fly out and back, and
 * a plan that has none cannot receive one. That is the same number, reported
 * the same way.
 */
public enum Injector {

    FERRY("ferry", AnomalyType.FERRY_UNNECESSARY, true),
    DELAY("delay", AnomalyType.DELAY, true),
    CANCELLATION("cxl", AnomalyType.CANCELLATION, true),
    AOG("aog", AnomalyType.AOG, true),
    MISPOSITIONED("mispos", AnomalyType.MISPOSITIONED, true),
    ROTATION_INEFFICIENT("rotIneff", AnomalyType.ROTATION_INEFFICIENT, true),
    REPOSITIONING("repos", AnomalyType.REPOSITIONING_UNNECESSARY, true),
    CREW_CONFLICT("crewConf", AnomalyType.CREW_CONFLICT, true),
    UNDER_CREW("underCrew", AnomalyType.UNDERUTILIZED_CREW, true),
    OVER_CREW("overCrew", AnomalyType.OVERUTILIZED_CREW, true),
    MX_CRITICAL("mxCrit", AnomalyType.MX_CRITICAL, true),

    /* Declared, not yet writing. See the class comment. */
    VIP("vip", AnomalyType.VIP_REQUEST, false),
    UNDER_AC("underAC", AnomalyType.UNDERUTILIZED_AC, false),
    OVER_AC("overAC", AnomalyType.OVERUTILIZED_AC, false),
    CAMO("camo", AnomalyType.CAMO_CONFLICT, false),
    OPPORTUNITY("oppty", AnomalyType.EMPTY_LEG_OPPORTUNITY, false),
    WEATHER("wx", AnomalyType.WX_IMPACT, false),
    AIRPORT_CLOSURE("aptClosure", AnomalyType.AIRPORT_CLOSURE, false),
    RUNWAY_CLOSURE("rwyClosure", AnomalyType.RWY_CLOSURE, false),
    PARKING_LIMIT("parkingLimit", AnomalyType.PARKING_LIMIT, false),
    CREW_REPOSITIONING("crewRepos", AnomalyType.CREW_REPOSITIONING, false),
    DORMANT("dormant", AnomalyType.DORMANT_AC, false),
    FAR_FROM_DEMAND("farDemand", AnomalyType.FAR_FROM_DEMAND, false),
    CLUSTER("cluster", AnomalyType.SAME_APT_CLUSTER, false);

    private final String key;
    private final AnomalyType anomaly;
    private final boolean implemented;

    Injector(String key, AnomalyType anomaly, boolean implemented) {
        this.key = key;
        this.anomaly = anomaly;
        this.implemented = implemented;
    }

    /** The key the prototype uses, and the one the API speaks. */
    public String key() {
        return key;
    }

    public AnomalyType anomaly() {
        return anomaly;
    }

    public boolean isImplemented() {
        return implemented;
    }

    public static Optional<Injector> byKey(String key) {
        return Arrays.stream(values()).filter(injector -> injector.key.equals(key)).findFirst();
    }
}
