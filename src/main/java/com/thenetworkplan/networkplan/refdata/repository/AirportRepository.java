package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.Airport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AirportRepository extends JpaRepository<Airport, UUID> {

    Optional<Airport> findByIcao(String icao);

    /** One indexed IN query instead of one round trip per station. */
    List<Airport> findByIcaoIn(Collection<String> icaoCodes);

    /**
     * The directory search: ICAO, IATA, name or city, case-insensitive.
     *
     * <p>{@code search} arrives lower-cased and surrounded by {@code %}, or
     * null for the whole set — the same convention as the crew search, so a
     * reader who has seen one has seen both.
     *
     * <p><b>It takes a {@link Pageable}, and the caller always passes one.</b>
     * The table held twenty-six rows when this was written and now holds nine
     * and a half thousand; an unbounded select was harmless then and is four
     * megabytes now. Pushing the cap into SQL rather than trimming the list in
     * Java is the difference between reading two hundred rows and reading all
     * of them to throw most away.
     */
    @Query("""
            select a from Airport a
            where (:search is null
                   or lower(a.icao) like :search
                   or lower(a.iata) like :search
                   or lower(a.name) like :search
                   or lower(a.city) like :search)
              and (:country is null or a.countryIso2 = :country)
              and (:region is null or a.region = :region)
            order by a.icao
            """)
    List<Airport> search(@Param("search") String search,
                         @Param("country") String country,
                         @Param("region") Short region,
                         Pageable page);

    /** How many aerodromes that same search matches, cap or no cap. */
    @Query("""
            select count(a) from Airport a
            where (:search is null
                   or lower(a.icao) like :search
                   or lower(a.iata) like :search
                   or lower(a.name) like :search
                   or lower(a.city) like :search)
              and (:country is null or a.countryIso2 = :country)
              and (:region is null or a.region = :region)
            """)
    long countMatching(@Param("search") String search,
                       @Param("country") String country,
                       @Param("region") Short region);
}
