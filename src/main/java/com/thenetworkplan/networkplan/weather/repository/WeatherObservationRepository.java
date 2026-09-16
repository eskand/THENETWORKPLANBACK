package com.thenetworkplan.networkplan.weather.repository;

import com.thenetworkplan.networkplan.weather.domain.WeatherObservation;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherObservationRepository extends JpaRepository<WeatherObservation, UUID> {

    /**
     * The observations of a set of stations, newest first.
     *
     * <p>The service keeps the first row per station, so one statement serves
     * a whole OCC screen whatever the number of bases.
     */
    @Query("""
            select o from WeatherObservation o
            where o.stationIcao in :stations
              and o.reportType = 'METAR'
              and o.observedAt >= :since
            order by o.stationIcao, o.observedAt desc
            """)
    List<WeatherObservation> findRecent(@Param("stations") Collection<String> stations,
                                        @Param("since") OffsetDateTime since);

    Optional<WeatherObservation> findByStationIcaoAndReportTypeAndObservedAt(
            String stationIcao, String reportType, OffsetDateTime observedAt);
}
