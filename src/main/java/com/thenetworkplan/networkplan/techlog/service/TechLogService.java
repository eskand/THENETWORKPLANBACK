package com.thenetworkplan.networkplan.techlog.service;

import com.thenetworkplan.networkplan.techlog.dto.DefectDto;
import com.thenetworkplan.networkplan.techlog.dto.ReportDefectCommand;
import com.thenetworkplan.networkplan.techlog.dto.ResolveDefectCommand;
import com.thenetworkplan.networkplan.techlog.dto.SaveTechLogEntryCommand;
import com.thenetworkplan.networkplan.techlog.dto.TechLogBoardDto;
import com.thenetworkplan.networkplan.techlog.dto.TechLogEntryDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Electronic tech log.
 *
 * <p>{@link #countOutstandingDefects} is the door the CAMO module knocks on,
 * the same way CAMO asks CAMO Admin for its outstanding directives: modules
 * answer for their own tables.
 */
public interface TechLogService {

    List<TechLogEntryDto> findPages(UUID tenantId, UUID aircraftId, LocalDate from, LocalDate to);

    TechLogEntryDto findPage(UUID tenantId, UUID entryId);

    TechLogEntryDto createPage(UUID tenantId, SaveTechLogEntryCommand command);

    /**
     * Signing the page. This is what feeds {@code camo.utilisation} and moves
     * TSN/CSN, through {@code UtilisationRecorder} — the single writer.
     */
    TechLogEntryDto sign(UUID tenantId, UUID entryId, UUID engineerId);

    List<DefectDto> findDefects(UUID tenantId, UUID aircraftId, boolean openOnly);

    DefectDto reportDefect(UUID tenantId, UUID entryId, ReportDefectCommand command);

    /** Closes the defect, or defers it under a MEL line — one or the other. */
    DefectDto resolveDefect(UUID tenantId, UUID defectId, ResolveDefectCommand command);

    Map<UUID, Integer> countOutstandingDefects(UUID tenantId);

    /**
     * The defect picture of the whole fleet, with its figures.
     *
     * <p>One call rather than six: the counts and the rows must describe the
     * same moment, and a board assembled from several requests cannot promise
     * that.
     */
    TechLogBoardDto findBoard(UUID tenantId);
}
