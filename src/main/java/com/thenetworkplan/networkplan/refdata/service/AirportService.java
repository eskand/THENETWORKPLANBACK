package com.thenetworkplan.networkplan.refdata.service;

import com.thenetworkplan.networkplan.refdata.dto.AirportDto;
import java.util.Collection;
import java.util.Map;

/**
 * Read access to the aerodrome reference set.
 *
 * <p>An interface rather than a class so that the dispatch read model depends on
 * the capability, not on JPA: the day the reference set moves behind an HTTP
 * service (annexe A2, DOM8) only the implementation changes.
 */
public interface AirportService {

    AirportDto findByIcao(String icao);

    /** ICAO to airport, resolved in one query. Missing codes are simply absent. */
    Map<String, AirportDto> findAllByIcao(Collection<String> icaoCodes);
}
