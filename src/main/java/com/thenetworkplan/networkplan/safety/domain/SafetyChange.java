package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One management-of-change record — ORO.GEN.200(a)(3).
 *
 * <p>Adding a type to the AOC, moving flight watch in house: a change that
 * alters how the operation is run has to be assessed before it happens, not
 * explained after. The two risk indices are before and after mitigation, and
 * the database refuses a residual higher than the initial — measures that
 * raise the risk are not measures.
 */
@Entity
@Table(name = "changes", schema = "safety")
@Getter
@Setter
public class SafetyChange extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description")
    private String description;

    /** What the change touches; it decides who has to be consulted. */
    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "raised_on", nullable = false)
    private LocalDate raisedOn;

    @Column(name = "effective_on")
    private LocalDate effectiveOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "status", nullable = false)
    private String status = "ASSESSING";

    @Column(name = "owner_name")
    private String ownerName;

    @Column(name = "initial_index")
    private Short initialIndex;

    @Column(name = "residual_index")
    private Short residualIndex;

    @Column(name = "mitigation")
    private String mitigation;

    /** Still being assessed or rolled out. */
    public boolean isActive() {
        return !"CLOSED".equals(status) && !"REJECTED".equals(status);
    }
}
