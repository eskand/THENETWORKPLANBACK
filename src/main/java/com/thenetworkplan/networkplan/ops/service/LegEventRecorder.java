package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import java.util.Map;
import java.util.UUID;

/**
 * Appends to a leg's history.
 *
 * <p>Its own collaborator rather than code inlined in every service: the history
 * is the audit trail, and it should be impossible to change a leg through a path
 * that forgets to record why.
 */
public interface LegEventRecorder {

    void record(UUID tenantId,
                UUID legId,
                LegEventKind kind,
                Map<String, Object> before,
                Map<String, Object> after,
                UUID actorId,
                String reason);
}
