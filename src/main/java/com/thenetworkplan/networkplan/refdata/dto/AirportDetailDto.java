package com.thenetworkplan.networkplan.refdata.dto;

import com.thenetworkplan.networkplan.tripsupport.dto.SupplierDto;
import java.io.Serializable;
import java.util.List;

/**
 * The aerodrome file: reference data, runways, operator notes, suppliers and
 * how much the operator uses the station.
 *
 * <p>Suppliers come from the trip support module through its service, not from
 * its table — the boundary the dispatch board set.
 */
public record AirportDetailDto(
        AirportDto airport,
        List<RunwayDto> runways,
        List<AirportNoteDto> notes,
        List<SupplierDto> suppliers,
        /** Les frequences publiees : tour, ATIS, sol. */
        List<AirportFrequencyDto> frequencies,
        /** L annuaire : qui assiste, qui avitaille, qui traite. */
        List<AirportServiceDto> services,
        long legsLast90Days,
        long departuresLast90Days,
        long arrivalsLast90Days) implements Serializable {
}
