package com.thenetworkplan.networkplan.reporting.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.reporting.domain.ReportDefinition;
import com.thenetworkplan.networkplan.reporting.domain.ReportRun;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportDefinitionDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportResultDto;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos.ReportRunDto;
import com.thenetworkplan.networkplan.reporting.repository.ReportDefinitionRepository;
import com.thenetworkplan.networkplan.reporting.repository.ReportRunRepository;
import com.thenetworkplan.networkplan.reporting.service.ReportRunner;
import com.thenetworkplan.networkplan.reporting.service.ReportingService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reports.
 *
 * <p>The catalogue is in the database; the answers are computed on demand by
 * the runners Spring hands in, keyed by code. A definition with no runner is
 * reported as such rather than answered with an empty table — an empty report
 * and a report nobody has written are different facts.
 */
@Service
@Transactional(readOnly = true)
public class ReportingServiceImpl implements ReportingService {

    private final ReportDefinitionRepository definitionRepository;
    private final ReportRunRepository runRepository;
    private final Map<String, ReportRunner> runners;

    public ReportingServiceImpl(ReportDefinitionRepository definitionRepository,
                                ReportRunRepository runRepository,
                                List<ReportRunner> runners) {
        this.definitionRepository = definitionRepository;
        this.runRepository = runRepository;
        this.runners = runners.stream()
                .collect(Collectors.toMap(ReportRunner::code, Function.identity()));
    }

    @Override
    public List<ReportDefinitionDto> findCatalogue(UUID tenantId) {
        Map<UUID, ReportRun> lastRun = new HashMap<>();
        for (ReportRun run : runRepository.findRecent(tenantId)) {
            lastRun.putIfAbsent(run.getDefinition().getId(), run);
        }
        return definitionRepository.findByTenantIdOrderByDomainAscCodeAsc(tenantId).stream()
                .map(definition -> {
                    ReportRun run = lastRun.get(definition.getId());
                    return new ReportDefinitionDto(
                            definition.getId(),
                            definition.getCode(),
                            definition.getTitle(),
                            definition.getDomain(),
                            definition.getDescription(),
                            definition.getModule(),
                            definition.getSubtitle(),
                            definition.getScope(),
                            runners.containsKey(definition.getCode()),
                            definition.getDefaultWindowDays(),
                            run == null ? null : run.getRanAt(),
                            run == null ? null : run.getRowCount());
                })
                .toList();
    }

    @Override
    public List<ReportRunDto> findRecentRuns(UUID tenantId) {
        return runRepository.findRecent(tenantId).stream()
                .map(run -> new ReportRunDto(
                        run.getId(),
                        run.getDefinition().getCode(),
                        run.getDefinition().getTitle(),
                        run.getRanAt(),
                        run.getWindowFrom(),
                        run.getWindowTo(),
                        run.getRowCount(),
                        run.getDurationMs()))
                .toList();
    }

    @Override
    @Transactional
    public ReportResultDto run(UUID tenantId, String code, LocalDate from, LocalDate to, UUID actorId) {
        ReportDefinition definition = definitionRepository
                .findByTenantIdAndCode(tenantId, code.trim().toUpperCase())
                .orElseThrow(() -> ResourceNotFoundException.of("Report", code));

        ReportRunner runner = runners.get(definition.getCode());
        if (runner == null) {
            throw new BusinessRuleException("REPORT_NOT_IMPLEMENTED",
                    "Report " + definition.getCode() + " is in the catalogue but has no runner yet. "
                            + "An empty table would read as \"nothing to report\", which is not the same thing.");
        }
        if (to.isBefore(from)) {
            throw new BusinessRuleException("REPORT_WINDOW_INVALID",
                    "The window ends before it starts");
        }

        long startedAt = System.nanoTime();
        List<List<String>> rows = runner.rows(tenantId, from, to);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;

        // The execution is recorded, so the catalogue can say when each report
        // was last produced and how big the answer was.
        ReportRun run = new ReportRun();
        run.setTenantId(tenantId);
        run.setDefinition(definition);
        run.setRanAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setRanBy(actorId);
        run.setWindowFrom(from);
        run.setWindowTo(to);
        run.setRowCount(rows.size());
        run.setDurationMs((int) durationMs);
        runRepository.save(run);

        return new ReportResultDto(
                definition.getCode(),
                definition.getTitle(),
                definition.getDomain(),
                from,
                to,
                runner.columns(),
                rows,
                rows.size(),
                durationMs,
                runner.kpis(tenantId, from, to),
                runner.charts(tenantId, from, to),
                runner.note(),
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
