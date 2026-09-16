package com.thenetworkplan.networkplan.safety.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One cell of the operator's 5×5 matrix.
 *
 * <p>In the database rather than in the code, because it is the operator's
 * risk appetite: moving a cell from tolerable to unacceptable is a management
 * decision recorded in the safety management manual, not a release.
 */
@Entity
@Table(name = "risk_matrix", schema = "safety")
@Getter
@Setter
public class RiskMatrixCell extends BaseEntity {

    /** A (catastrophic) to E (negligible). */
    @Column(name = "severity", nullable = false)
    private String severity;

    /** 5 (frequent) to 1 (extremely improbable). */
    @Column(name = "probability", nullable = false)
    private int probability;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    /**
     * The numeric index of this cell: severity value times probability, 1 to 25.
     *
     * <p>Stored on the cell rather than computed per screen, so the level and
     * the index always come from the same row. A dashboard that bands on the
     * index and a register that bands on the level cannot then disagree.
     */
    @Column(name = "risk_index", nullable = false)
    private short riskIndex;

    /** A = 5 catastrophic down to E = 1 negligible (ICAO Doc 9859). */
    @Column(name = "severity_value", nullable = false)
    private short severityValue;

    @Column(name = "action_required")
    private String actionRequired;
}
