package com.thenetworkplan.networkplan.dispatch.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchBoardDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchFilter;
import com.thenetworkplan.networkplan.dispatch.service.DispatchBoardService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
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
