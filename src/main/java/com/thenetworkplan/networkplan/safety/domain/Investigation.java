package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One investigation into an occurrence.
 *
 * <p>The database refuses to close an investigation without a root cause. An
 * investigation closed with an empty cause was not concluded, it was abandoned,
 * and the two must not look alike in a list a regulator reads.
 */
@Entity
@Table(name = "investigations", schema = "safety")
@Getter
@Setter
public class Investigation extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occurrence_id")
    private Occurrence occurrence;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "investigator")
    private UUID investigator;

    @Column(name = "investigator_name")
    private String investigatorName;

    @Column(name = "opened_on", nullable = false)
    private LocalDate openedOn;

    @Column(name = "target_on")
    private LocalDate targetOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "status", nullable = false)
    private String status = "OPEN";

    /** The method followed — it tells the reader what depth to expect. */
    @Column(name = "method")
    private String method;

    @Column(name = "findings")
    private String findings;

    @Column(name = "root_cause")
    private String rootCause;

    @Column(name = "contributing_factors")
    private String contributingFactors;

    public boolean isOpen() {
        return !"CLOSED".equals(status);
    }

    /** Past its target date and still running. */
    public boolean isOverdue(LocalDate on) {
        return isOpen() && targetOn != null && targetOn.isBefore(on);
    }
}
