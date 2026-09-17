package com.thenetworkplan.networkplan.refdata.service;

import com.thenetworkplan.networkplan.refdata.dto.AirportDetailDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportDirectoryDto;
import java.util.List;
import java.util.UUID;

/**
 * Airports Data — the single aerodrome directory.
 *
 * <p>Separate from {@link AirportService}, which is the narrow read the
 * dispatch board uses and which is cached for twelve hours. This one composes
 * runways, operator notes, suppliers and real usage: a different question, a
 * different cost, and no cache.
 */
public interface AirportDirectoryService {

    /**
     * Search the directory.
     *
     * <p><b>Bounded by construction.</b> {@code limit} caps what comes back;
     * {@link AirportDirectoryDto#matched()} says how many the search really
     * found, so a screen can tell an operator to narrow it rather than quietly
     * showing the first two hundred as if they were all of them.
     *
     * @param region  the directory's own region number, or null for all seven
     * @param limit   maximum rows to return; clamped to a sane ceiling
     */
    AirportDirectoryDto search(UUID tenantId, String search, String country,
                               Short region, boolean usedOnly, int limit);

    AirportDetailDto findDetail(UUID tenantId, String icao);
}
