package com.thenetworkplan.networkplan.permits.repository;

import com.thenetworkplan.networkplan.permits.domain.FirBoundary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FirBoundaryRepository extends JpaRepository<FirBoundary, UUID> {

    /**
     * The FIRs whose bounding box overlaps the box of the route.
     *
     * <p>The cheap filter before the expensive test. A Paris–Dubai great circle
     * spans roughly 0–56 E and 25–50 N; this cuts two hundred and fifty
     * candidates down to about thirty before a single ring is walked.
     */
    @Query("""
            select f from FirBoundary f
            where f.lonMax >= :lonMin and f.lonMin <= :lonMax
              and f.latMax >= :latMin and f.latMin <= :latMax
            """)
    List<FirBoundary> findOverlapping(@Param("lonMin") double lonMin,
                                      @Param("lonMax") double lonMax,
                                      @Param("latMin") double latMin,
                                      @Param("latMax") double latMax);
}
