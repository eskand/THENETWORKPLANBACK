package com.thenetworkplan.networkplan.refdata.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A PPR, a curfew, a slot regime, a customs arrangement.
 *
 * <p>The audit found two competing aerodrome directories in the prototype.
 * There is one here, and an operator's own knowledge about a station is a row
 * attached to it — with a validity window and a provenance, so it can expire
 * and can be traced.
 */
@Entity
@Table(name = "airport_notes", schema = "refdata")
@Getter
@Setter
public class AirportNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "airport_id", nullable = false)
    private Airport airport;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "detail")
    private String detail;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "severity", nullable = false)
    private String severity = "INFO";

    public boolean isInForce(LocalDate on) {
        return (validFrom == null || !on.isBefore(validFrom))
                && (validTo == null || !on.isAfter(validTo));
    }
}
