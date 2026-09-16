package com.thenetworkplan.networkplan.common.exception;

import java.io.Serial;

/**
 * An operational rule refuses the command: an AOG aircraft cannot be assigned,
 * a release cannot be signed while a blocking check fails, a leg cannot be moved
 * after departure. The rule reference is carried so the front end can show why.
 */
public class BusinessRuleException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String rule;

    public BusinessRuleException(String rule, String message) {
        super(message);
        this.rule = rule;
    }

    public String getRule() {
        return rule;
    }
}
