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
 * One published radio frequency of an aerodrome.
 *
 * <p>A row per frequency, not a column per service: a tower can publish two,
 * and "118.100, 119.700" in one column is a list that has to be split again on
 * every read — and split differently by whoever reads it next.
 */
@Entity
@Table(name = "airport_frequencies", schema = "refdata")
@Getter
@Setter
public class AirportFrequency {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "airport_id", nullable = false)
    private UUID airportId;

    /** TOWER, ATIS, GROUND, APPROACH. */
    @Column(name = "service", nullable = false)
    private String service;

    /** Stored as published: "118.675". Not a number — leading zeros matter. */
    @Column(name = "mhz", nullable = false)
    private String mhz;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
