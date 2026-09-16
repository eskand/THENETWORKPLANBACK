package com.thenetworkplan.networkplan.dispatch.dto;

import java.time.LocalDate;

/**
 * What the board is being asked for: a day, a tab, and the two selectors of the
 * toolbar.
 *
 * @param fleetType ICAO type to restrict to, or null for the whole fleet
 * @param baseIcao  operating base to restrict to, or null for every base
 */
public record DispatchFilter(
        LocalDate date,
        DispatchTab tab,
        String fleetType,
        String baseIcao) {

    public static DispatchFilter of(LocalDate date, String tab, String fleetType, String baseIcao) {
        return new DispatchFilter(
                date != null ? date : LocalDate.now(),
                DispatchTab.parse(tab),
                blankToNull(fleetType),
                blankToNull(baseIcao));
    }

    /** Stable cache key: the board is cached per tenant and per exact request. */
    public String cacheKey() {
        return date + "|" + tab + "|" + (fleetType == null ? "*" : fleetType)
                + "|" + (baseIcao == null ? "*" : baseIcao);
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.toUpperCase();
    }
}
