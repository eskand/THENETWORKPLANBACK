package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.dto.RecordMovementCommand;
import java.util.UUID;

/** API13 — OOOI times, MVT and flight closure. */
public interface LegMovementService {

    LegDto recordMovement(UUID tenantId, UUID legId, RecordMovementCommand command, UUID actorId);

    /** Marks the movement message as actually sent, with the time it left. */
    LegDto markMvtSent(UUID tenantId, UUID legId, UUID actorId);

    /** Closes the leg once it is on blocks: times are final from here. */
    LegDto close(UUID tenantId, UUID legId, UUID actorId);
}
