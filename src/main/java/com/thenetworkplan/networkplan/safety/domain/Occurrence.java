package com.thenetworkplan.networkplan.safety.domain;

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

/**
 * A safety occurrence, from the report to the closure.
 *
 * <p>Two audit findings live in this table. The ECCAIRS fields exist, so a
 * report can be filed with the authority instead of staying in the product.
 * And the risk verdict is <em>stored</em> with its date and its assessor,
 * rather than being recomputed every time a screen draws the 5×5 matrix.
 *
 * <p>{@code anonymous} is honoured all the way: when it is true the reporter
 * is not returned by the read model, even though the row keeps it — a just
 * culture needs both the protection and the traceability.
 */
@Entity
@Table(name = "occurrences", schema = "safety")
@Getter
@Setter
public class Occurrence extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "occurred_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "reported_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime reportedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private Person reportedBy;

    /**
     * What kind of report the person filed.
     *
     * <p>Null on the rows that predate the reporting form: the category was
     * recorded, the reporter's own choice of words was not, and inventing one
     * afterwards would put words in their mouth.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "report_type")
    private ReportType reportType;

    /** What was done at the time. The first thing an investigator asks. */
    @Column(name = "immediate_action")
    private String immediateAction;

    /** What the reporter thinks would prevent it happening again. */
    @Column(name = "reporter_suggestion")
    private String reporterSuggestion;

    /**
     * The Safety Manager knows who reported and de-identifies before sharing.
     *
     * <p>Distinct from {@link #isAnonymous()}, where nobody knows and nobody
     * can come back for a detail. An anonymous report is confidential by
     * construction; the database enforces that, not this class.
     */
    /**
     * Le declarant, en clair.
     *
     * <p>{@code reportedBy} pointe sur le registre equipage. Un regulateur ou
     * un agent d escale declare aussi, et son nom n a nulle part ou aller :
     * cette colonne le garde, quel que soit le registre d origine.
     */
    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "reporter_role")
    private String reporterRole;

    /* --- la question posee au declarant --- */

    @Column(name = "query_text")
    private String queryText;

    @Column(name = "query_asked_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime queryAskedAt;

    @Column(name = "query_asked_by")
    private String queryAskedBy;

    @Column(name = "query_answer")
    private String queryAnswer;

    @Column(name = "query_answered_at", columnDefinition = "timestamptz")
    private java.time.OffsetDateTime queryAnsweredAt;

    /** Une question sans reponse bloque le dossier. */
    public boolean hasOpenQuery() {
        return queryText != null && queryAnswer == null;
    }

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous;

    /** Plain identifier: DOM6 does not join DOM1. */
    @Column(name = "leg_id")
    private UUID legId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aircraft_id")
    private Aircraft aircraft;

    @Column(name = "station_icao")
    private String stationIcao;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private OccurrenceCategory category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "narrative", nullable = false)
    private String narrative;

    @Column(name = "phase_of_flight")
    private String phaseOfFlight;

    @Column(name = "eccairs_event_type")
    private String eccairsEventType;

    @Column(name = "eccairs_occurrence_class")
    private String eccairsOccurrenceClass;

    @Column(name = "eccairs_exported_at", columnDefinition = "timestamptz")
    private OffsetDateTime eccairsExportedAt;

    @Column(name = "eccairs_reference")
    private String eccairsReference;

    @Column(name = "risk_severity")
    private String riskSeverity;

    @Column(name = "risk_probability")
    private Integer riskProbability;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "risk_assessed_at", columnDefinition = "timestamptz")
    private OffsetDateTime riskAssessedAt;

    @Column(name = "risk_assessed_by")
    private UUID riskAssessedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OccurrenceStatus status = OccurrenceStatus.REPORTED;

    @Column(name = "closed_at", columnDefinition = "timestamptz")
    private OffsetDateTime closedAt;

    @Column(name = "closed_by")
    private UUID closedBy;
}
