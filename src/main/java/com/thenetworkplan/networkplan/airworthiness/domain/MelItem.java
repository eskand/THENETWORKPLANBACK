package com.thenetworkplan.networkplan.airworthiness.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
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

/**
 * An open MEL item and the operational limitation it carries.
 *
 * <p>The audit's gap: the flight strip never showed the MEL items in force, so a
 * dispatcher could release a flight without seeing "no RVSM" or "no CAT II".
 * {@link #blocksDispatch} is what the readiness check reads.
 */
@Entity
@Table(name = "mel_items", schema = "camo")
@Getter
@Setter
public class MelItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false)
    private Aircraft aircraft;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "mel_category", nullable = false)
    private MelCategory melCategory;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "limitation")
    private String limitation;

    @Column(name = "raised_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime raisedAt;

    @Column(name = "due_at", columnDefinition = "timestamptz")
    private OffsetDateTime dueAt;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @Column(name = "blocks_dispatch", nullable = false)
    private boolean blocksDispatch;

    /**
     * The operator MEL line this deferral rests on (V11).
     *
     * <p>A plain identifier: the library belongs to the MEL module, which owns
     * that table. Null on an item raised before the library existed.
     */
    @Column(name = "mel_library_id")
    private UUID melLibraryId;

    @Column(name = "raised_by")
    private UUID raisedBy;

    @Column(name = "closed_by")
    private UUID closedBy;

    /** A category that requires a placard is not deferred until it is fitted. */
    @Column(name = "placard_fitted", nullable = false)
    private boolean placardFitted;

    public boolean isOpen() {
        return closedAt == null;
    }
}
