package com.thenetworkplan.networkplan.training.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.crew.domain.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * A completed course in someone's file.
 *
 * <p>{@code validTo} is written once, when the record is created, from the
 * validity of the course on that day. It is never recomputed at display time:
 * if the operator shortens a cycle next year, past records keep the expiry they
 * were issued with, and an inspector sees what the crew member was told.
 */
@Entity
@Table(name = "training_records", schema = "crew")
@Getter
@Setter
public class TrainingRecord extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private TrainingCourse course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private TrainingSession session;

    @Column(name = "completed_on", nullable = false)
    private LocalDate completedOn;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "score")
    private BigDecimal score;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Person instructor;

    @Column(name = "reference")
    private String reference;
}
