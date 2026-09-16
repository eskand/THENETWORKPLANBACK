package com.thenetworkplan.networkplan.refdata.repository;

import com.thenetworkplan.networkplan.refdata.domain.AircraftReference;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AircraftReferenceRepository extends JpaRepository<AircraftReference, UUID> {

    /**
     * Free-text search over the reference.
     *
     * <p>Matches the designator, the manufacturer and the model, because those
     * are the three things anyone has to hand: a flight plan gives "F2TH", a
     * contract says "Falcon 2000", a fleet list says "Dassault".
     *
     * <p>Paged rather than complete: three hundred rows are cheap, but this is
     * a lookup field and nobody reads past the first screen of it.
     */
    @Query("""
            select r from AircraftReference r
            where :pattern is null
               or lower(r.model) like :pattern
               or lower(r.manufacturer) like :pattern
               or lower(r.icaoType) like :pattern
            order by r.manufacturer, r.model
            """)
    List<AircraftReference> search(@Param("pattern") String pattern, Pageable pageable);

    long count();
}
