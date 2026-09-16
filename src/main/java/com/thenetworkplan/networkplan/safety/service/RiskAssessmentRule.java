package com.thenetworkplan.networkplan.safety.service;

import com.thenetworkplan.networkplan.safety.domain.RiskLevel;
import com.thenetworkplan.networkplan.safety.domain.RiskMatrixCell;
import java.util.List;

/**
 * Reads the operator's 5×5 matrix.
 *
 * <p>A rule with no repository: the cells are passed in, already loaded. The
 * verdict therefore depends only on the matrix the operator has declared —
 * and, crucially, a severity and probability pair that the matrix does not
 * cover produces no verdict at all rather than a default.
 */
public interface RiskAssessmentRule {

    /**
     * @param cells the operator matrix, all twenty-five cells
     * @return the level for that pair, or null when the matrix does not define
     *         it — which the caller must surface, not swallow
     */
    RiskLevel level(List<RiskMatrixCell> cells, String severity, int probability);
}
