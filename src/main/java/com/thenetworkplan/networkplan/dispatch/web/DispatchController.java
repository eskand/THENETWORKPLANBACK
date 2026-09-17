package com.thenetworkplan.networkplan.dispatch.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchBoardDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchFilter;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchRowDto;
import com.thenetworkplan.networkplan.dispatch.service.DispatchBoardService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The single endpoint behind the Dispatch screen. */
@RestController
@RequestMapping("/v1/dispatch")
public class DispatchController {

    private final DispatchBoardService dispatchBoardService;

    public DispatchController(DispatchBoardService dispatchBoardService) {
        this.dispatchBoardService = dispatchBoardService;
    }

    /**
     * The flight file's own read — the same row the board shows, for one leg.
     *
     * <p>Under /dispatch rather than /legs because what it returns is the
     * dispatch view of a leg: services, permits, crew and MEL folded in. The
     * ops module owns the leg; this owns the picture an OCC reads of it.
     */
    @GetMapping("/legs/{legId}")
    public DispatchRowDto leg(@PathVariable UUID legId) {
        return dispatchBoardService.findRow(TenantContext.require(), legId);
    }

    @GetMapping("/board")
    public DispatchBoardDto board(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "tab", required = false) String tab,
            @RequestParam(name = "fleet", required = false) String fleetType,
            @RequestParam(name = "base", required = false) String baseIcao) {
        DispatchFilter filter = DispatchFilter.of(date, tab, fleetType, baseIcao);
        return dispatchBoardService.load(TenantContext.require(), filter);
    }
}
