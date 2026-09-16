package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;

/**
 * One readiness finding.
 *
 * @param check    stable identifier of the check, for the UI and for tests
 * @param severity BLOCKING, DEROGABLE or INFO
 * @param message  what is missing, in words a dispatcher can act on
 * @param rule     the rule or regulation reference behind the check
 */
public record ReadinessItem(
        String check,
        String severity,
        String message,
        String rule) implements Serializable {

    public static final String BLOCKING = "BLOCKING";
    public static final String DEROGABLE = "DEROGABLE";
    public static final String INFO = "INFO";

    public static ReadinessItem blocking(String check, String message, String rule) {
        return new ReadinessItem(check, BLOCKING, message, rule);
    }

    public static ReadinessItem derogable(String check, String message, String rule) {
        return new ReadinessItem(check, DEROGABLE, message, rule);
    }

    public static ReadinessItem info(String check, String message, String rule) {
        return new ReadinessItem(check, INFO, message, rule);
    }
}
