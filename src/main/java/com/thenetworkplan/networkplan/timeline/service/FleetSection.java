package com.thenetworkplan.networkplan.timeline.service;

/**
 * The heading a registration sits under on the Flight Timeline.
 *
 * <p>The audited prototype carried these as literal separator rows inside its
 * fleet array ({@code { section: "Falcon Fleet" }}), so adding a tail meant
 * remembering to file it by hand. Here the heading is derived from the
 * aircraft type, in one place: a new registration lands under the right
 * heading because of what it is, not because someone put it there.
 *
 * <p>An ICAO type nobody has classified yet returns {@link #OTHER} rather
 * than being quietly filed under the nearest-looking family.
 */
public final class FleetSection {

    public static final String OTHER = "Other Fleet";

    private FleetSection() {
    }

    public static String of(String icaoType) {
        if (icaoType == null || icaoType.isBlank()) {
            return OTHER;
        }
        String type = icaoType.trim().toUpperCase();
        return switch (type) {
            case "F2TH", "F900", "FA7X", "FA8X", "F2EX" -> "Falcon Fleet";
            case "C525", "C25A", "C25B", "C25C", "C25M" -> "Citation 525-Family Fleet";
            case "E35L", "E135", "E145" -> "Legacy Fleet";
            case "E190", "E290", "E90L" -> "Embraer Lineage 1000 Fleet (E190 VIP)";
            default -> OTHER;
        };
    }
}
