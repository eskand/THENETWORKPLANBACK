package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

/**
 * One safety performance indicator, and what it is measured against.
 *
 * <p>Only the definition lives here. The value is computed at read time from
 * the operational data — occurrences, overdue actions, crew currency, MEL
 * items, airworthiness review certificates — because an indicator stored as a
 * number stops moving the moment it is written, and a safety indicator that
 * does not move is worse than none.
 */
@Entity
@Table(name = "spi_definitions", schema = "safety")
@Getter
@Setter
public class SpiDefinition extends BaseEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "unit")
    private String unit;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "target_value", nullable = false)
    private BigDecimal targetValue;

    /** Crossing this convenes a safety review board; missing the target does not. */
    @Column(name = "alert_value")
    private BigDecimal alertValue;

    /** LOWER or HIGHER — which way is good. */
    @Column(name = "direction", nullable = false)
    private String direction;

    /** The service that produces the value. Named so the gap is visible. */
    @Column(name = "computed_by")
    private String computedBy;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    /**
     * Whether a measured value meets the target.
     *
     * <p>Direction is the whole point: 38.5 against a target of 85 is a miss,
     * and a screen that simply compares two numbers would call it a pass.
     */
    public boolean meetsTarget(BigDecimal value) {
        if (value == null) {
            return false;
        }
        return "LOWER".equals(direction)
                ? value.compareTo(targetValue) <= 0
                : value.compareTo(targetValue) >= 0;
    }

    /** Past the level that convenes a review. */
    public boolean breachesAlert(BigDecimal value) {
        if (value == null || alertValue == null) {
            return false;
        }
        return "LOWER".equals(direction)
                ? value.compareTo(alertValue) >= 0
                : value.compareTo(alertValue) <= 0;
    }
}
