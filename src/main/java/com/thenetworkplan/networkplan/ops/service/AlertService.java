package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.AlertDto;
import java.util.List;
import java.util.UUID;

/** FR15 / FR40 — the ranked alert wall and its acknowledgements. */
public interface AlertService {

    List<AlertDto> findOpen(UUID tenantId);

    AlertDto acknowledge(UUID tenantId, UUID alertId, UUID actorId);
}
