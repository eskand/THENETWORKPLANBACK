package com.thenetworkplan.networkplan.training.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import com.thenetworkplan.networkplan.crew.domain.Person;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/** One dated run of a course, with its capacity and its instructor. */
@Entity
@Table(name = "training_sessions", schema = "crew")
@Getter
@Setter
public class TrainingSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private TrainingCourse course;

    @Column(name = "starts_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime endsAt;

    @Column(name = "location")
    private String location;

    @Column(name = "capacity", nullable = false)
    private int capacity = 12;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Person instructor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status = SessionStatus.PLANNED;
}
