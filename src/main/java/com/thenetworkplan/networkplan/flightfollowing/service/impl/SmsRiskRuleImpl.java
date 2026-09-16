package com.thenetworkplan.networkplan.flightfollowing.service.impl;

import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** See {@link SmsRiskRule}. Pure arithmetic on five scores, no state, no clock. */
@Component
public class SmsRiskRuleImpl implements SmsRiskRule {

    @Override
    public Assessment assess(List<Scored> factors) {
        List<Scored> scored = factors == null ? List.of() : factors;

        int severity = 1;
        int active = 0;
        List<Factor> unknown = new ArrayList<>();

        for (Scored entry : scored) {
            if (entry == null || entry.level() == null) {
                continue;
            }
            if (entry.level() == Level.UNKNOWN) {
                unknown.add(entry.factor());
                continue;
            }
            severity = Math.max(severity, entry.level().severity());
            if (entry.level().active()) {
                active++;
            }
        }

        // Likelihood scaled by how many factors are live at once: one problem
        // is a problem, four at once is a pattern. The prototype's ladder.
        int likelihood = switch (Math.min(active, 4)) {
            case 0 -> 1;
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 4;
            default -> 5;
        };

        int index = severity * likelihood;
        String level = index >= 15 ? "CRITICAL"
                : index >= 10 ? "HIGH"
                : index >= 5 ? "MEDIUM"
                : "LOW";

        return new Assessment(level, severity, likelihood, index, action(level, unknown),
                scored, List.copyOf(unknown));
    }

    /**
     * What the level asks of the operator.
     *
     * <p>The sentences are the prototype's, with one addition: when a source
     * has not answered, the action says so. "Acceptable risk" computed over a
     * weather feed that never replied is not an acceptable risk, it is an
     * unassessed one.
     */
    private String action(String level, List<Factor> unknown) {
        String base = switch (level) {
            case "CRITICAL" -> "Immediate action required: notify supervisor, activate ERP if "
                    + "applicable, prepare mitigation before further progress.";
            case "HIGH" -> "Mitigation required: review affected factors with supervisor, brief "
                    + "crew, increase monitoring frequency.";
            case "MEDIUM" -> "Monitor and reassess: track trend, confirm mitigations remain valid, "
                    + "document in shift log.";
            default -> "Acceptable risk: routine Flight Watch monitoring continues.";
        };
        if (unknown.isEmpty()) {
            return base;
        }
        List<String> names = unknown.stream().map(Factor::label).toList();
        return base + " Assessed without " + String.join(", ", names)
                + ": that source has not answered, so this level is a floor, not a verdict.";
    }
}
