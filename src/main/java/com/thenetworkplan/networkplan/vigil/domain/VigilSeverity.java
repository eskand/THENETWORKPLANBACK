package com.thenetworkplan.networkplan.vigil.domain;

/** Les quatre gravites de l'annexe, dans l'ordre de {@code SEV_RANK}. */
public enum VigilSeverity {
    INFO(1),
    WARNING(2),
    HIGH(3),
    CRITICAL(4);

    private final int rank;

    VigilSeverity(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
