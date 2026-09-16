package com.thenetworkplan.networkplan.crew.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A period during which a crew member cannot be rostered.
 *
 * <p>Inclusive on both ends, as the check constraint says: a one-day sick leave
 * has {@code starts_on = ends_on}.
 */
@Entity
@Table(name = "absences", schema = "crew")
@Getter
@Setter
public class Absence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private AbsenceKind kind;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "reason")
    private String reason;

    public boolean covers(LocalDate day) {
        return !day.isBefore(startsOn) && !day.isAfter(endsOn);
    }
}
