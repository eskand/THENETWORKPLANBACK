package com.thenetworkplan.networkplan.reporting.service;

import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportDefinitionDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportResultDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportRunDto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Reports — the catalogue, the runs, and the answers. */
public interface ReportingService {

    List<ReportDefinitionDto> findCatalogue(UUID tenantId);

    List<ReportRunDto> findRecentRuns(UUID tenantId);

    /** Runs the report and records the execution. */
    ReportResultDto run(UUID tenantId, String code, LocalDate from, LocalDate to, UUID actorId);
}
