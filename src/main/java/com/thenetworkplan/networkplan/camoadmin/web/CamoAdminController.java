package com.thenetworkplan.networkplan.camoadmin.web;

import com.thenetworkplan.networkplan.camoadmin.dto.ComplyDirectiveCommand;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveApplicationDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.AuditEventDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.CamoAdminBoardDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.ComponentDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.DocumentDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.UserDto;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveDto;
import com.thenetworkplan.networkplan.camoadmin.service.CamoAdminBoardService;
import com.thenetworkplan.networkplan.camoadmin.dto.ProgrammeTaskDto;
import com.thenetworkplan.networkplan.camoadmin.dto.SaveDirectiveCommand;
import com.thenetworkplan.networkplan.camoadmin.dto.SaveProgrammeTaskCommand;
import com.thenetworkplan.networkplan.camoadmin.service.CamoAdminService;
import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API31 — CAMO Admin: maintenance programme and airworthiness directives. */
@RestController
@RequestMapping("/v1/camo-admin")
public class CamoAdminController {

    private final CamoAdminService camoAdminService;
    private final CamoAdminBoardService boardService;

    public CamoAdminController(CamoAdminService camoAdminService,
                               CamoAdminBoardService boardService) {
        this.camoAdminService = camoAdminService;
        this.boardService = boardService;
    }

    /**
     * The back office at a glance, with the alerts it found.
     *
     * <p>Nothing here is stored: every figure and every alert is derived from
     * the rows at the moment of reading.
     */
    @GetMapping("/board")
    public CamoAdminBoardDto board() {
        return boardService.findBoard(TenantContext.require());
    }

    /** The component register. Four screens, one table, one category filter. */
    @GetMapping("/components")
    public List<ComponentDto> components(@RequestParam(required = false) String category) {
        return boardService.findComponents(TenantContext.require(), category);
    }

    @GetMapping("/documents")
    public List<DocumentDto> documents(@RequestParam(required = false) String category) {
        return boardService.findDocuments(TenantContext.require(), category);
    }

    @GetMapping("/users")
    public List<UserDto> users() {
        return boardService.findUsers(TenantContext.require());
    }

    @GetMapping("/audit")
    public List<AuditEventDto> audit(@RequestParam(defaultValue = "200") int limit) {
        return boardService.findAuditTrail(TenantContext.require(), limit);
    }

    @GetMapping("/programme")
    public List<ProgrammeTaskDto> programme(@RequestParam(name = "icaoType", required = false) String icaoType) {
        return camoAdminService.findProgramme(TenantContext.require(), icaoType);
    }

    @PutMapping("/programme")
    public ProgrammeTaskDto saveProgrammeTask(@Valid @RequestBody SaveProgrammeTaskCommand command) {
        return camoAdminService.saveProgrammeTask(TenantContext.require(), command);
    }

    @GetMapping("/directives")
    public List<DirectiveDto> directives(
            @RequestParam(name = "outstandingOnly", defaultValue = "false") boolean outstandingOnly) {
        return camoAdminService.findDirectives(TenantContext.require(), outstandingOnly);
    }

    @GetMapping("/directives/{id}")
    public DirectiveDto directive(@PathVariable UUID id) {
        return camoAdminService.findDirective(TenantContext.require(), id);
    }

    @PostMapping("/directives")
    @ResponseStatus(HttpStatus.CREATED)
    public DirectiveDto createDirective(@Valid @RequestBody SaveDirectiveCommand command) {
        return camoAdminService.createDirective(TenantContext.require(), command);
    }

    @PatchMapping("/directive-applications/{id}")
    public DirectiveApplicationDto comply(@PathVariable UUID id,
                                          @Valid @RequestBody ComplyDirectiveCommand command) {
        return camoAdminService.comply(TenantContext.require(), id, command);
    }

    @GetMapping("/aircraft/{id}/directives")
    public List<DirectiveApplicationDto> byAircraft(@PathVariable UUID id) {
        return camoAdminService.findByAircraft(TenantContext.require(), id);
    }
}
