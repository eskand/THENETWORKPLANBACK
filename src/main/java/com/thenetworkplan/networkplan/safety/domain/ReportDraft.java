package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * A safety report someone started and has not sent.
 *
 * <p>A report is often written twice: a few lines while it is fresh, the rest
 * after the flight. Without somewhere to put the first half, the person who
 * gets interrupted does not come back — and the report that never gets filed
 * is the one the safety management system most needed.
 *
 * <p><b>A draft belongs to its author and to nobody else.</b> It is not yet a
 * report; reading it before it is sent would be reading over someone's
 * shoulder, and an operator that does that stops receiving reports.
 *
 * <p>The draft survives submission, with {@link #getSubmittedOccurrenceId()}
 * pointing at what it became: the reporter has to be able to see what they
 * actually sent.
 */
@Entity
@Table(name = "report_drafts", schema = "safety")
@Getter
@Setter
public class ReportDraft extends BaseEntity {

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name", nullable = false)
    private String authorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type")
    private ReportType reportType;

    @Column(name = "title")
    private String title;

    @Column(name = "occurred_at")
    private OffsetDateTime occurredAt;

    @Column(name = "phase_of_flight")
    private String phaseOfFlight;

    @Column(name = "station_icao")
    private String stationIcao;

    @Column(name = "flight_no")
    private String flightNo;

    @Column(name = "registration")
    private String registration;

    @Column(name = "narrative")
    private String narrative;

    @Column(name = "immediate_action")
    private String immediateAction;

    @Column(name = "reporter_suggestion")
    private String reporterSuggestion;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "confidential", nullable = false)
    private boolean confidential;

    @Column(name = "submitted_occurrence_id")
    private UUID submittedOccurrenceId;

    @Column(name = "submitted_at")
    private OffsetDateTime submittedAt;

    public boolean isSubmitted() {
        return submittedAt != null;
    }

    /**
     * Whether the draft holds enough to be sent.
     *
     * <p>A type, a title and a narrative. Everything else on the form helps the
     * analysis and none of it should stop someone reporting: a hazard described
     * in one line and nothing else is still worth having.
     */
    public boolean isSubmittable() {
        return reportType != null
                && title != null && !title.isBlank()
                && narrative != null && !narrative.isBlank();
    }
}
