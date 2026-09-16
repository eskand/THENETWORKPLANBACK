package com.thenetworkplan.networkplan.erp.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
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
import com.thenetworkplan.networkplan.erp.service.ErpConsoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The crisis console.
 *
 * <p>Every command returns the whole console rather than the row it changed.
 * Under pressure the screen must not be able to show a ticked box next to a
 * progress figure that has not caught up with it.
 */
@RestController
@RequestMapping("/v1/erp/console")
public class ErpConsoleController {

    private final ErpConsoleService consoleService;

    public ErpConsoleController(ErpConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @GetMapping
    public ConsoleDto console() {
        return consoleService.findConsole(TenantContext.require());
    }

    @GetMapping("/catalogue")
    public CatalogueDto catalogue() {
        return consoleService.findCatalogue();
    }

    @GetMapping("/reference")
    public ReferenceDto reference() {
        return consoleService.findReference(TenantContext.require());
    }

    @PostMapping("/assess")
    public AssessmentDto assess(@RequestBody AssessCommand command) {
        return consoleService.assess(TenantContext.require(), command);
    }

    @GetMapping("/checklists")
    public List<ChecklistDto> checklists(@RequestParam(required = false) UUID activationId) {
        return consoleService.findChecklists(TenantContext.require(), activationId);
    }

    @GetMapping("/templates")
    public List<TemplateDto> templates(@RequestParam(required = false) UUID activationId) {
        return consoleService.findTemplates(TenantContext.require(), activationId);
    }

    @PostMapping("/checks")
    public ConsoleDto check(@RequestParam(required = false) UUID activationId,
                            @Valid @RequestBody CheckCommand command) {
        return consoleService.check(TenantContext.require(), activationId, command);
    }

    @PostMapping("/notifications")
    public ConsoleDto notified(@RequestParam(required = false) UUID activationId,
                               @Valid @RequestBody NotifyCommand command) {
        return consoleService.notify(TenantContext.require(), activationId, command);
    }

    @PatchMapping("/level")
    public ConsoleDto level(@RequestParam(required = false) UUID activationId,
                            @Valid @RequestBody LevelCommand command) {
        return consoleService.changeLevel(TenantContext.require(), activationId, command);
    }

    @PostMapping("/sitreps")
    public ConsoleDto sitrep(@RequestParam(required = false) UUID activationId,
                             @Valid @RequestBody SitrepCommand command) {
        return consoleService.addSitrep(TenantContext.require(), activationId, command);
    }

    @PatchMapping("/subject")
    public ConsoleDto subject(@RequestParam(required = false) UUID activationId,
                              @Valid @RequestBody SubjectCommand command) {
        return consoleService.updateSubject(TenantContext.require(), activationId, command);
    }

    @PostMapping("/log")
    public ConsoleDto log(@RequestParam(required = false) UUID activationId,
                          @Valid @RequestBody LogCommand command) {
        return consoleService.log(TenantContext.require(), activationId, command);
    }
}
