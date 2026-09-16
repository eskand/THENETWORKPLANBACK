package com.thenetworkplan.networkplan.ops.dto;

import com.thenetworkplan.networkplan.ops.domain.LegStatus;

/** One row of a {@code group by status} aggregate over the day's programme. */
public record LegStatusCount(LegStatus status, Long count) {
}
