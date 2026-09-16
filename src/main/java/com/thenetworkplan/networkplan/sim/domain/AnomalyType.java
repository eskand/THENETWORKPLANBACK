package com.thenetworkplan.networkplan.sim.domain;

/**
 * What the generator can put wrong in a scenario.
 *
 * <p>The twenty-eight of the approved prototype, with the two things that make
 * a scenario an exercise rather than a degraded plan: how badly it hurts, and
 * <em>what the solver is expected to do about it</em>. Without the expected
 * fix there is no mark scheme — a dispatcher can be given the scenario, but
 * nobody can say afterwards whether they solved it.
 */
public enum AnomalyType {

    FERRY_UNNECESSARY("Unnecessary ferry", Severity.MEDIUM, "REMOVE_FERRY"),
    DELAY("Delay", Severity.LOW, "RESEQUENCE"),
    CANCELLATION("Cancellation", Severity.MEDIUM, "RECOVER_DEMAND"),
    REPOSITIONING_AFTER_CXL("Repositioning after cancellation", Severity.MEDIUM, "REMOVE_FERRY"),
    AOG("Aircraft on ground", Severity.CRITICAL, "REASSIGN_FLEET"),
    VIP_REQUEST("Last-minute VIP request", Severity.HIGH, "ASSIGN_AIRCRAFT"),
    MISPOSITIONED("Mispositioned aircraft", Severity.HIGH, "REPOSITION_OPTIMALLY"),
    UNDERUTILIZED_AC("Under-utilised aircraft", Severity.MEDIUM, "REBALANCE_FLYING"),
    OVERUTILIZED_AC("Over-utilised aircraft", Severity.MEDIUM, "REBALANCE_FLYING"),
    ROTATION_INEFFICIENT("Inefficient rotation", Severity.MEDIUM, "DIRECT_ROUTING"),
    REPOSITIONING_UNNECESSARY("Unnecessary repositioning", Severity.MEDIUM, "REMOVE_FERRY"),
    CREW_CONFLICT("Crew conflict (FTL)", Severity.CRITICAL, "REASSIGN_CREW"),
    UNDERUTILIZED_CREW("Under-utilised crew", Severity.LOW, "REBALANCE_CREW"),
    OVERUTILIZED_CREW("Over-utilised crew", Severity.HIGH, "REBALANCE_CREW"),
    MX_CRITICAL("Critical maintenance", Severity.CRITICAL, "RESCHEDULE_MX"),
    CAMO_CONFLICT("CAMO conflict", Severity.CRITICAL, "GROUND_OR_RESCHEDULE"),
    COMBINABLE_FLIGHTS("Same-client sectors split", Severity.LOW, "MERGE_SAME_CLIENT"),
    EMPTY_LEG_OPPORTUNITY("Sellable empty leg", Severity.LOW, "MARKET_EMPTY_LEG"),
    DISPATCH_NOT_READY("Flight not dispatch-ready", Severity.HIGH, "COMPLETE_DISPATCH"),
    WX_IMPACT("Weather impact", Severity.HIGH, "REROUTE_OR_DELAY"),
    AIRPORT_CLOSURE("Airport unavailability", Severity.CRITICAL, "DIVERT_PLANNING"),
    RWY_CLOSURE("Runway closure", Severity.HIGH, "DIVERT_PLANNING"),
    PARKING_LIMIT("Parking limitation", Severity.MEDIUM, "REDISTRIBUTE_PARKING"),
    CREW_REPOSITIONING("Crew repositioning needed", Severity.MEDIUM, "OPTIMIZE_CREW_POS"),
    DORMANT_AC("Dormant aircraft", Severity.MEDIUM, "REBALANCE_FLYING"),
    FAR_FROM_DEMAND("Aircraft far from demand", Severity.HIGH, "REPOSITION_OPTIMALLY"),
    SAME_APT_CLUSTER("Fleet clustered on one airport", Severity.MEDIUM, "REDISTRIBUTE_FLEET"),
    ADDED_FLIGHT("Manually added flight", Severity.LOW, "REVIEW");

    /** Four levels, the same four the safety scan uses. One vocabulary. */
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    private final String label;
    private final Severity severity;
    private final String expectedFix;

    AnomalyType(String label, Severity severity, String expectedFix) {
        this.label = label;
        this.severity = severity;
        this.expectedFix = expectedFix;
    }

    public String label() {
        return label;
    }

    public Severity severity() {
        return severity;
    }

    /** The move the solver is expected to make. The scenario's mark scheme. */
    public String expectedFix() {
        return expectedFix;
    }
}
