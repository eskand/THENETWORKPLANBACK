package com.thenetworkplan.networkplan.camo.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One life-limited part fitted to one registration.
 *
 * <p>A turbine disc or a landing-gear pin does not wear out on a schedule the
 * maintenance programme can move: it has a published life, and at the end of it
 * the part comes off whatever else is planned. These are the items that ground
 * an aircraft with no check due and no defect open, which is why they have
 * their own tab and not a line in the task list.
 *
 * <p><b>A limit and a consumption, never a percentage.</b> The prototype stored
 * the remaining life as a number between 0 and 100 — a figure that cannot move
 * when the aircraft flies. Here the published limit and what has been consumed
 * are stored; how much is left is asked for.
 *
 * <p>A part can carry up to three limits at once and the nearest one governs;
 * {@link #fractionRemaining} is the minimum across those that are set.
 */
@Entity
@Table(name = "life_limited_parts", schema = "camo")
@Getter
@Setter
public class LifeLimitedPart extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "part_no")
    private String partNo;

    @Column(name = "serial_no")
    private String serialNo;

    /** ENG1, ENG2, MLG-L, APU — two discs of the same part number wear differently. */
    @Column(name = "position")
    private String position;

    @Column(name = "limit_hours")
    private BigDecimal limitHours;

    @Column(name = "limit_cycles")
    private Integer limitCycles;

    @Column(name = "limit_months")
    private Integer limitMonths;

    @Column(name = "used_hours", nullable = false)
    private BigDecimal usedHours = BigDecimal.ZERO;

    @Column(name = "used_cycles", nullable = false)
    private int usedCycles;

    @Column(name = "installed_on")
    private LocalDate installedOn;

    /** Set when the part comes off. The row stays; it leaves the calculation. */
    @Column(name = "removed_at", columnDefinition = "timestamptz")
    private OffsetDateTime removedAt;

    @Column(name = "remark")
    private String remark;

    public boolean isFitted() {
        return removedAt == null;
    }

    /**
     * Share of life left, between 0 and 1, governed by the nearest limit.
     *
     * <p>A part past a limit returns 0 rather than a negative share: it is not
     * "minus eight per cent remaining", it is finished, and the overrun belongs
     * in the record, not in the gauge.
     */
    public double fractionRemaining(LocalDate on) {
        double worst = 1.0;
        if (limitCycles != null && limitCycles > 0) {
            worst = Math.min(worst, 1.0 - (double) usedCycles / limitCycles);
        }
        if (limitHours != null && limitHours.signum() > 0 && usedHours != null) {
            worst = Math.min(worst, 1.0 - usedHours.doubleValue() / limitHours.doubleValue());
        }
        if (limitMonths != null && limitMonths > 0 && installedOn != null) {
            double used = java.time.temporal.ChronoUnit.MONTHS.between(installedOn, on);
            worst = Math.min(worst, 1.0 - used / limitMonths);
        }
        return Math.max(0.0, worst);
    }

    /** Whole cycles left on the cycle limit, or null when the part has none. */
    public Integer cyclesRemaining() {
        if (limitCycles == null) {
            return null;
        }
        return Math.max(0, limitCycles - usedCycles);
    }
}
