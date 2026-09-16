package com.thenetworkplan.networkplan.permits.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One flight information region, with its bounding box and its rings.
 *
 * <p>The bounding box is stored beside the rings on purpose: it is the cheap
 * filter that runs before the point-in-polygon test. A route from Paris to
 * Dubai touches about thirty of the two hundred and fifty FIRs on file, and
 * testing every ring of every one of them for every sampled point would cost
 * millions of comparisons for nothing.
 */
@Entity
@Table(name = "fir_boundaries", schema = "refdata")
@Getter
@Setter
public class FirBoundary {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "fir_code", nullable = false)
    private String firCode;

    @Column(name = "fir_name")
    private String firName;

    @Column(name = "country_name")
    private String countryName;

    @Column(name = "lon_min", nullable = false)
    private double lonMin;

    @Column(name = "lat_min", nullable = false)
    private double latMin;

    @Column(name = "lon_max", nullable = false)
    private double lonMax;

    @Column(name = "lat_max", nullable = false)
    private double latMax;

    /** Rings as [[ [lon, lat], … ], … ], exactly as the annexe supplies them. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "polygons", nullable = false, columnDefinition = "jsonb")
    private String polygons;

    @Column(name = "valid_as_of", nullable = false)
    private LocalDate validAsOf;
}
