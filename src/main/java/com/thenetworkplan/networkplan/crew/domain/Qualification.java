package com.thenetworkplan.networkplan.crew.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
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
 * One qualification held by one crew member.
 *
 * <p>The audit found the prototype rendered ratings from a string on the crew
 * card, so a rating could not expire and could not be checked. Here a rating is
 * a row with a validity window, and the window is what dispatch reads.
 */
@Entity
@Table(name = "qualifications", schema = "crew")
@Getter
@Setter
public class Qualification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    /** Null for the kinds that are not tied to a type (CRM, SEP, dangerous goods). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_type_id")
    private AircraftType aircraftType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private QualificationKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private QualificationLevel level;

    @Column(name = "valid_from")
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    /**
     * CAT_I to CAT_IIIC, on the LVO row and nowhere else.
     *
     * <p>Null everywhere else, and null on an LVO row that has not been
     * graded. A pilot with no LVO qualification at all is CAT I — the company
     * standard — so the absence of a row is an answer, not a gap.
     */
    @Column(name = "approach_category")
    private String approachCategory;

    @Column(name = "reference")
    private String reference;
}
