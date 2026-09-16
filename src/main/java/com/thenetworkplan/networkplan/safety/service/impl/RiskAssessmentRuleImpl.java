package com.thenetworkplan.networkplan.safety.service.impl;

import com.thenetworkplan.networkplan.safety.domain.RiskLevel;
import com.thenetworkplan.networkplan.safety.domain.RiskMatrixCell;
import com.thenetworkplan.networkplan.safety.service.RiskAssessmentRule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A lookup, and nothing else.
 *
 * <p>There is deliberately no arithmetic here — no "severity times
 * probability". The operator's matrix may be asymmetric, and often is: a
 * catastrophic-but-improbable cell is a management decision, not a product of
 * two numbers.
 */
@Component
public class RiskAssessmentRuleImpl implements RiskAssessmentRule {

    @Override
    public RiskLevel level(List<RiskMatrixCell> cells, String severity, int probability) {
        return cells.stream()
                .filter(cell -> cell.getSeverity().equalsIgnoreCase(severity)
                        && cell.getProbability() == probability)
                .map(RiskMatrixCell::getRiskLevel)
                .findFirst()
                .orElse(null);
    }
}
