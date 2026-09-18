package com.thenetworkplan.networkplan.optimizer.web;

import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizationResultDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizeCommand;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizerSetupDto;
import com.thenetworkplan.networkplan.optimizer.service.OptimizerService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Le Timeline Optimizer — {@code window.TNPOptimizer} de l'annexe (l. 95582). */
@RestController
@RequestMapping("/v1/timeline/optimize")
public class OptimizerController {

    private final OptimizerService optimizerService;

    public OptimizerController(OptimizerService optimizerService) {
        this.optimizerService = optimizerService;
    }

    /** Les modules lus, les perimetres, les couts par defaut — ce que la fenetre affiche avant l'analyse. */
    @GetMapping("/setup")
    public OptimizerSetupDto setup() {
        return optimizerService.setup(TenantContext.require());
    }

    /** « Analyse & propose optimizations » — {@code TNPOptimizer.run(o)}. Ne modifie rien. */
    @PostMapping
    public OptimizationResultDto optimize(@Valid @RequestBody OptimizeCommand command) {
        return optimizerService.optimize(TenantContext.require(), command);
    }
}
