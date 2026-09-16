package com.thenetworkplan.networkplan.erp.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.erp.dto.ErpCommands.ActivateCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpCommands.StandDownCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpActivationDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpBoardDto;
import com.thenetworkplan.networkplan.erp.service.ErpService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API63 — ERP: the plan, the call tree, the activations. */
@RestController
@RequestMapping("/v1/erp")
public class ErpController {

    private final ErpService erpService;

    public ErpController(ErpService erpService) {
        this.erpService = erpService;
    }

    @GetMapping("/board")
    public ErpBoardDto board() {
        return erpService.findBoard(TenantContext.require());
    }

    @PostMapping("/activations")
    @ResponseStatus(HttpStatus.CREATED)
    public ErpActivationDto activate(@Valid @RequestBody ActivateCommand command,
                                     @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return erpService.activate(TenantContext.require(), command, actorId);
    }

    @PatchMapping("/activations/{id}/stand-down")
    public ErpActivationDto standDown(@PathVariable UUID id, @Valid @RequestBody StandDownCommand command) {
        return erpService.standDown(TenantContext.require(), id, command);
    }
}
