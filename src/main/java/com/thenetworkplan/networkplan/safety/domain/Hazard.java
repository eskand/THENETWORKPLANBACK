package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * One hazard on the register.
 *
 * <p><b>Both cotations are stored, and neither index is.</b> The initial rating
 * is what the hazard is worth with no barrier in place; the residual is what it
 * is worth with the barriers that exist. The difference between them <em>is</em>
 * the measure of what the barriers buy — keeping only the residual makes it
 * impossible to show that a control does anything.
 *
 * <p>Severity times likelihood is arithmetic, so it is computed. A stored index
 * drifts from its own two factors at the first reassessment, and the index is
 * the figure an auditor reads first.
 */
@Entity
@Table(name = "hazards", schema = "safety")
@Getter
@Setter
public class Hazard extends BaseEntity {

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "hazard", nullable = false)
    private String hazard;

    /** The credible consequence, not the worst imaginable: it is what is rated. */
    @Column(name = "consequence")
    private String consequence;

    @Column(name = "domain", nullable = false)
    private String domain;

    @Column(name = "category")
    private String category;

    /** reactive, proactive or predictive — how the hazard came to be known. */
    @Column(name = "identification", nullable = false)
    private String identification = "proactive";

    @Column(name = "severity_initial", nullable = false)
    private String severityInitial;

    @Column(name = "likelihood_initial", nullable = false)
    private short likelihoodInitial;

    @Column(name = "severity_residual", nullable = false)
    private String severityResidual;

    @Column(name = "likelihood_residual", nullable = false)
    private short likelihoodResidual;

    @Column(name = "owner")
    private String owner;

    @Column(name = "review_on")
    private LocalDate reviewOn;

    @Column(name = "status", nullable = false)
    private String status = "open";

    @Column(name = "notes")
    private String notes;

    /** ICAO Doc 9859: A catastrophic is 5, E negligible is 1. */
    public static int severityValue(String severity) {
        return switch (severity) {
            case "A" -> 5;
            case "B" -> 4;
            case "C" -> 3;
            case "D" -> 2;
            default -> 1;
        };
    }

    public int initialIndex() {
        return severityValue(severityInitial) * likelihoodInitial;
    }

    public int residualIndex() {
        return severityValue(severityResidual) * likelihoodResidual;
    }

    /**
     * How much of the risk the barriers actually remove, as a percentage.
     *
     * <p>Zero when nothing changed — which is the answer a register should give
     * for a hazard whose controls exist only on paper.
     */
    public int reductionPercent() {
        int initial = initialIndex();
        return initial == 0 ? 0 : Math.round((1 - (float) residualIndex() / initial) * 100);
    }
}
