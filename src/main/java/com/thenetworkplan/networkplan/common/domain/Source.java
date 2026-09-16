package com.thenetworkplan.networkplan.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Provenance of a row (ADR-13). The audit found that a third of the values the
 * prototype displayed were fabricated; every persisted row now carries where it
 * came from, so a value can always be traced back to a reference set, a
 * timestamped human entry or a deterministic engine.
 *
 * <p>The shape is fixed, so it is five plain columns rather than a jsonb blob:
 * queryable, indexable and checked by the database.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Source {

    /** manual, refdata, engine, roster, integration, seed. */
    @Column(name = "source_type", nullable = false)
    private String type;

    /** Free reference: e-mail thread, AIP, AFM, supplier name, message id. */
    @Column(name = "source_ref")
    private String reference;

    /** Version of the reference set or of the engine that produced the value. */
    @Column(name = "source_version")
    private String version;

    /** User who entered or triggered the value, when there is one. */
    @Column(name = "source_author")
    private UUID author;

    @Column(name = "source_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime at;

    public static Source of(String type, String reference) {
        return new Source(type, reference, null, null, OffsetDateTime.now());
    }

    public static Source manual(UUID author, String reference) {
        return new Source("manual", reference, null, author, OffsetDateTime.now());
    }

    public static Source engine(String engineName, String engineVersion) {
        return new Source("engine", engineName, engineVersion, null, OffsetDateTime.now());
    }
}
