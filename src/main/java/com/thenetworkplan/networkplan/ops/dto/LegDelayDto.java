package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * One coded delay, against one leg.
 *
 * <p>Exposed so the punctuality report can print the cause beside the sector.
 * The aggregate {@code countDelaysByCode} answers « how many minutes of code
 * 41 » and that is the wrong question for a Pareto: two delays of the same code
 * can have different remarks, and it is the remark an operator recognises.
 */
public record LegDelayDto(
        UUID legId,
        int minutes,
        String code,
        String subCode,
        String remark) implements Serializable {

    /** The cause as it reads on a report: the remark when there is one. */
    public String cause() {
        return remark == null || remark.isBlank() ? code : remark;
    }
}
