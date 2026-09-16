package com.thenetworkplan.networkplan.erp.service;

import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.AssessCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.CheckCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.LevelCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.LogCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.NotifyCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.SitrepCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleCommands.SubjectCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.AssessmentDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.CatalogueDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ChecklistDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ConsoleDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.ReferenceDto;
import com.thenetworkplan.networkplan.erp.dto.ErpConsoleDtos.TemplateDto;
import java.util.List;
import java.util.UUID;

/**
 * The crisis console.
 *
 * <p>The severity engine lives here rather than in the browser, for one
 * reason: the level decides who is called, which notifications start, and what
 * the operator says in public. A figure with those consequences is not computed
 * on a laptop that may be running a stale copy of the plan.
 */
public interface ErpConsoleService {

    /** Everything the console shows on opening: armed, or the live event. */
    ConsoleDto findConsole(UUID tenantId);

    /** The emergency catalogue and the questionnaire. */
    CatalogueDto findCatalogue();

    /** What level a situation is, and why. Changes nothing. */
    AssessmentDto assess(UUID tenantId, AssessCommand command);

    /** The departmental checklists at the level the crisis is running at. */
    List<ChecklistDto> findChecklists(UUID tenantId, UUID activationId);

    /** The templates with their tokens filled from the live event. */
    List<TemplateDto> findTemplates(UUID tenantId, UUID activationId);

    /** The plan itself, with no event in progress. */
    ReferenceDto findReference(UUID tenantId);

    ConsoleDto check(UUID tenantId, UUID activationId, CheckCommand command);

    ConsoleDto notify(UUID tenantId, UUID activationId, NotifyCommand command);

    ConsoleDto changeLevel(UUID tenantId, UUID activationId, LevelCommand command);

    ConsoleDto addSitrep(UUID tenantId, UUID activationId, SitrepCommand command);

    ConsoleDto updateSubject(UUID tenantId, UUID activationId, SubjectCommand command);

    ConsoleDto log(UUID tenantId, UUID activationId, LogCommand command);
}
