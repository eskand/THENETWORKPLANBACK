package com.thenetworkplan.networkplan.techlog.domain;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** A defect reported by the crew or found by maintenance. */
@Entity
@Table(name = "defects", schema = "camo")
@Getter
@Setter
public class Defect extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tech_log_entry_id")
    private TechLogEntry techLogEntry;

    @Column(name = "ata_chapter")
    private String ataChapter;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "reported_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime reportedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private Person reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DefectStatus status = DefectStatus.OPEN;

    /** The MEL item this defect was deferred under. Plain identifier. */
    @Column(name = "mel_item_id")
    private UUID melItemId;

    @Column(name = "corrective_action")
    private String correctiveAction;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;
}
