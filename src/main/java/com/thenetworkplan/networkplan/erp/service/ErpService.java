package com.thenetworkplan.networkplan.erp.service;

import com.thenetworkplan.networkplan.erp.domain.ErpActivation;
import com.thenetworkplan.networkplan.erp.domain.ErpExercise;
import com.thenetworkplan.networkplan.erp.dto.ErpCommands.ActivateCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpCommands.StandDownCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpActivationDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpBoardDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpExerciseDto;
import java.util.UUID;

/** DOM6 — the emergency response plan, its call tree and its activations. */
public interface ErpService {

    /** The current plan with its roles and its activation history. */
    ErpBoardDto findBoard(UUID tenantId);

    ErpActivationDto activate(UUID tenantId, ActivateCommand command, UUID actorId);

    ErpActivationDto standDown(UUID tenantId, UUID activationId, StandDownCommand command);

    /**
     * The read models, shared with the crisis console.
     *
     * <p>Exposed rather than duplicated: the level names and the authorisation
     * fields are read in two places, and two copies of a mapping like this one
     * would eventually disagree about what a level 3 is called.
     */
    ErpActivationDto toActivationDto(ErpActivation activation);

    ErpExerciseDto toExerciseDto(ErpExercise exercise);
}
