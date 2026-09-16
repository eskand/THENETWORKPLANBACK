package com.thenetworkplan.networkplan.refdata.service;

import com.thenetworkplan.networkplan.refdata.dto.AirportDetailDto;
import com.thenetworkplan.networkplan.refdata.dto.AirportRowDto;
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

    List<AirportRowDto> search(UUID tenantId, String search, String country, boolean usedOnly);

    AirportDetailDto findDetail(UUID tenantId, String icao);
}
