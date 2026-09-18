package com.thenetworkplan.networkplan.optimizer.service;

import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizationResultDto;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizeCommand;
import com.thenetworkplan.networkplan.optimizer.dto.OptimizerDtos.OptimizerSetupDto;
import java.util.UUID;

/**
 * Le Timeline Optimizer — le bouton « Optimize » de la Flight Timeline
 * (prototype l. 7653), qui ouvre {@code TNPOptimizer} (l. 95582).
 *
 * <p>Chez l'annexe, l'analyse tourne dans le navigateur sur un instantane du
 * plan et le « moteur » enregistre est declare {@code stub:true}, avec un taux
 * de resolution tire au sort ({@code solveRate = 0.6 + Math.random()*0.25},
 * l. 90794). Ici le detecteur, le modele de couts, le score et les
 * instructions sont les siens, portes sur la base ; ce qui n'est pas repris
 * est le tirage : chaque inefficience dans le perimetre recoit l'action que
 * son bareme attend, deterministe, et le dispatcher decide.
 *
 * <p>Rien n'est ecrit par l'analyse. Les instructions « applicables » le sont
 * par les routes ordinaires de l'etape (changement d'appareil, re-horodatage,
 * annulation), une par une, avec leur motif dans le journal.
 */
public interface OptimizerService {

    OptimizerSetupDto setup(UUID tenantId);

    OptimizationResultDto optimize(UUID tenantId, OptimizeCommand command);
}
