package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One experience report.
 *
 * <p><b>Not an occurrence.</b> Nothing went wrong — somebody learned something
 * and chose to pass it on. Keeping it in its own table is what lets the
 * occurrence register stay what it is: the record of things that did happen.
 *
 * <p>An anonymous REX carries no author. The column is empty, not merely hidden
 * from the screen — a name that is in the database is a name that leaks.
 */
@Entity
@Table(name = "rex", schema = "safety")
@Getter
@Setter
public class Rex extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "phase")
    private String phase;

    @Column(name = "aircraft_type")
    private String aircraftType;

    @Column(name = "location")
    private String location;

    /** The whole account. A REX stripped of its narrative is a slogan. */
    @Column(name = "narrative", nullable = false)
    private String narrative;

    @Column(name = "recommendation")
    private String recommendation;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_role")
    private String authorRole;

    @Column(name = "attribution", nullable = false)
    private String attribution = "named";

    @Column(name = "scope", nullable = false)
    private String scope = "fleet";

    @Column(name = "published_on")
    private LocalDate publishedOn;

    @Column(name = "status", nullable = false)
    private String status = "draft";

    public boolean isAnonymous() {
        return "anonymous".equals(attribution);
    }
}
