package com.thenetworkplan.networkplan.safety.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** One lesson drawn from an experience report. */
@Entity
@Table(name = "rex_lessons", schema = "safety")
@Getter
@Setter
public class RexLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "rex_id", nullable = false)
    private UUID rexId;

    @Column(name = "lesson", nullable = false)
    private String lesson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
