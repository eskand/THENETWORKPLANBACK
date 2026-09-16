package com.thenetworkplan.networkplan.crew.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A crew member and the three expiry dates dispatch has to respect. */
@Entity
@Table(name = "persons", schema = "crew")
@Getter
@Setter
public class Person extends BaseEntity {

    @Column(name = "staff_no", nullable = false)
    private String staffNo;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "main_role", nullable = false)
    private CrewRole mainRole;

    @Column(name = "base_icao")
    private String baseIcao;

    @Column(name = "licence_expiry")
    private LocalDate licenceExpiry;

    @Column(name = "medical_expiry")
    private LocalDate medicalExpiry;

    @Column(name = "training_expiry")
    private LocalDate trainingExpiry;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    /**
     * The name as a crew plan writes it: surname first, given name after.
     *
     * <p>It is not a stylistic choice. A roster, a crew list and a GENDEC are
     * read by scanning a column of surnames, and the approved prototype prints
     * "Ben Arbia Y." for that reason. The columns keep their own meaning —
     * {@code lastName} is the surname, {@code firstName} the given name — and
     * only the order of display is fixed here, in one place, so every screen
     * says the same thing.
     */
    public String fullName() {
        return lastName + " " + firstName;
    }
}
