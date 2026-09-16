package com.thenetworkplan.networkplan.sim.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One training scenario: a frozen copy of the plan, with things put wrong in it.
 *
 * <p><b>It never reads the live plan again.</b> The legs it works on are copies
 * in {@code sim.scenario_legs}, taken once at generation. A scenario that
 * re-read the operational plan would change under the trainee as dispatch got
 * on with its day, and could not be replayed with a second crew.
 *
 * <p><b>The seed makes it repeatable.</b> Same seed, same preset, same
 * scenario — which is what separates an exercise two teams can be compared on
 * from a one-off curiosity.
 */
@Entity
@Table(name = "scenarios", schema = "sim")
@Getter
@Setter
public class Scenario extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private Difficulty difficulty = Difficulty.MEDIUM;

    @Column(name = "horizon_from", nullable = false)
    private LocalDate horizonFrom;

    @Column(name = "horizon_days", nullable = false)
    private short horizonDays;

    @Column(name = "random_seed", nullable = false)
    private long randomSeed;

    @Column(name = "status", nullable = false)
    private String status = "DRAFT";

    /** What was asked of each injector. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested", columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> requested = new LinkedHashMap<>();

    /**
     * What each injector could actually place.
     *
     * <p>Kept beside {@link #getRequested()} and never merged with it: an
     * injector that found no room is indistinguishable from one that was never
     * asked, unless both numbers survive.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "applied", columnDefinition = "jsonb", nullable = false)
    private Map<String, Integer> applied = new LinkedHashMap<>();

    /** The copied plan before injection: aircraft, sectors, crew, block hours. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "baseline", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> baseline = new LinkedHashMap<>();

    @Column(name = "remark")
    private String remark;
}
