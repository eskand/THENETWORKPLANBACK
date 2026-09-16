package com.thenetworkplan.networkplan.refdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One service provider listed at an aerodrome.
 *
 * <p><b>Directory, not contract.</b> {@code tripsupport.suppliers} is the list
 * of suppliers this operator has an agreement with — it carries a contract
 * reference and a preferred flag. This is the other thing: who handles at Bou
 * Saada, whether we ever go there or not. Confusing the two would have made
 * the operator appear to hold thirteen thousand contracts.
 */
@Entity
@Table(name = "airport_services", schema = "refdata")
@Getter
@Setter
public class AirportService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "airport_id", nullable = false)
    private UUID airportId;

    /** FBO, HANDLING, TRIP_SUPPORT, FUEL, SUPERVISORY, CATERING, AUTHORITY. */
    @Column(name = "service_type", nullable = false)
    private String serviceType;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    /** The number that answers at three in the morning, when it differs. */
    @Column(name = "after_hours")
    private String afterHours;

    @Column(name = "fax")
    private String fax;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    /** "H24", "0600-2200". A service that closes at night is not discovered at 02:00. */
    @Column(name = "hours")
    private String hours;

    @Column(name = "fuel_brands")
    private String fuelBrands;

    @Column(name = "frequency")
    private String frequency;
}
