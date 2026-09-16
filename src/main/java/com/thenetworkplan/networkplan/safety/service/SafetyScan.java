package com.thenetworkplan.networkplan.safety.service;

import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.MonitoringSummaryDto;
import java.util.UUID;

/**
 * The live safety scan.
 *
 * <p>Its own interface because it is the one part of the safety module that
 * reaches across every other one, and because what it produces is never
 * written down: the result is true at the instant it is asked for and at no
 * other.
 */
public interface SafetyScan {

    MonitoringSummaryDto scan(UUID tenantId);
}
