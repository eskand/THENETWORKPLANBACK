package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/** A safety promotion campaign, and who has to acknowledge it. */
@Entity
@Table(name = "campaigns", schema = "safety")
@Getter
@Setter
public class Campaign extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "title", nullable = false)
    private String title;

    /**
     * ALERT, BULLETIN, LESSON, POLICY.
     *
     * <p>An alert asks for something before the next flight; a policy states a
     * standing rule. Filing both under one heading makes them look equivalent
     * on screen, which they are not.
     */
    @Column(name = "kind", nullable = false)
    private String kind = "BULLETIN";

    /** Who signs it. An unsigned safety instruction commits nobody. */
    @Column(name = "author_name")
    private String authorName;

    /** The day it appeared, distinct from the day a campaign starts running. */
    @Column(name = "published_on", nullable = false)
    private java.time.LocalDate publishedOn;

    @Column(name = "theme", nullable = false)
    private String theme;

    @Column(name = "message")
    private String message;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on")
    private LocalDate endsOn;

    /** ALL, FLIGHT_CREW, CABIN, MAINTENANCE, GROUND or OFFICE. */
    @Column(name = "audience", nullable = false)
    private String audience = "ALL";

    @Column(name = "status", nullable = false)
    private String status = "PLANNED";

    /**
     * Whether the campaign has to be acknowledged.
     *
     * <p>When it does, the reach shown on the screen is the number of
     * acknowledgements over the audience — a measured figure, not "sent to
     * everyone" reported as if it had been read.
     */
    @Column(name = "acknowledgement_required", nullable = false)
    private boolean acknowledgementRequired;
}
