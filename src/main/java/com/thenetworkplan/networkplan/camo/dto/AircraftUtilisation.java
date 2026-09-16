package com.thenetworkplan.networkplan.camo.dto;

import java.util.UUID;

/** Utilisation of one aircraft over one window, counted by the database. */
public record AircraftUtilisation(UUID aircraftId, Long blockMinutes, Long cycles, Long flights) {

    public long blockMinutesOrZero() {
        return blockMinutes == null ? 0L : blockMinutes;
    }

    public long cyclesOrZero() {
        return cycles == null ? 0L : cycles;
    }
}
