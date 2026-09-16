package com.thenetworkplan.networkplan.tripsupport.service;

import com.thenetworkplan.networkplan.tripsupport.dto.SupplierDto;
import com.thenetworkplan.networkplan.tripsupport.dto.TripSupportBoardDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * NetPlus Services — the trip support desk.
 *
 * <p>A read model over DOM2: the permits and the ground services of every leg
 * of the day, plus the suppliers that can take a request. Same construction as
 * the dispatch board, on a narrower question.
 */
public interface TripSupportBoardService {

    TripSupportBoardDto findBoard(UUID tenantId, LocalDate date);

    /** The supplier directory, optionally for one station. */
    List<SupplierDto> findSuppliers(UUID tenantId, String stationIcao);
}
