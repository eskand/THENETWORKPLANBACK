package com.thenetworkplan.networkplan.safety.service;

import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.MonitoringSummaryDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.SafetyOverviewDto;
import java.util.List;
import java.util.UUID;

/**
 * The Safety Manager dashboard.
 *
 * <p>Separate from {@code SafetyService}, which records and manages
 * occurrences. This one only reads, and it reads across every module — the
 * two have no business sharing a transaction boundary.
 */
public interface SafetyOverviewService {

    SafetyOverviewDto findOverview(UUID tenantId);

    /**
     * The indicators alone, without the cross-module scan.
     *
     * <p>Safety Promotion publishes the objectives in its sidebar and has no use
     * for the scan's findings, which cost six module reads.
     */
    List<com.thenetworkplan.networkplan.safety.dto.SafetyOverviewDtos.SpiDto>
            findIndicators(UUID tenantId);

    /** Runs the live scan on its own, for the button that asks for it. */
    MonitoringSummaryDto runScan(UUID tenantId);
}
