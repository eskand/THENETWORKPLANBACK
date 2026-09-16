package com.thenetworkplan.networkplan.mel.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.mel.dto.CloseMelCommand;
import com.thenetworkplan.networkplan.mel.dto.MelEntryDto;
import com.thenetworkplan.networkplan.mel.dto.MelLibraryItemDto;
import com.thenetworkplan.networkplan.mel.dto.RaiseMelCommand;
import com.thenetworkplan.networkplan.mel.service.MelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API32 — MEL / CDL / HIL: the operator library and the deferrals in force. */
@RestController
@RequestMapping("/v1/mel")
public class MelController {

    private final MelService melService;

    public MelController(MelService melService) {
        this.melService = melService;
    }

    @GetMapping("/library")
    public List<MelLibraryItemDto> library(@RequestParam(name = "icaoType", required = false) String icaoType,
                                           @RequestParam(name = "ataChapter", required = false) String ataChapter) {
        return melService.findLibrary(TenantContext.require(), icaoType, ataChapter);
    }

    @GetMapping("/items")
    public List<MelEntryDto> open() {
        return melService.findOpen(TenantContext.require());
    }

    @GetMapping("/aircraft/{id}/items")
    public List<MelEntryDto> forAircraft(@PathVariable UUID id,
                                         @RequestParam(name = "openOnly", defaultValue = "true") boolean openOnly) {
        return melService.findForAircraft(TenantContext.require(), id, openOnly);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public MelEntryDto raise(@Valid @RequestBody RaiseMelCommand command) {
        return melService.raise(TenantContext.require(), command);
    }

    @PatchMapping("/items/{id}/close")
    public MelEntryDto close(@PathVariable UUID id, @Valid @RequestBody CloseMelCommand command) {
        return melService.close(TenantContext.require(), id, command);
    }
}
