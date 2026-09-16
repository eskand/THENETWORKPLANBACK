package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.AcknowledgeReleaseCommand;
import com.thenetworkplan.networkplan.ops.dto.ReleaseDto;
import com.thenetworkplan.networkplan.ops.dto.SignReleaseCommand;
import java.util.Optional;
import java.util.UUID;

/** API13 — signing and acknowledging the dispatch release. */
public interface ReleaseService {

    Optional<ReleaseDto> findCurrent(UUID tenantId, UUID legId);

    /**
     * Signs the release, refusing while any blocking finding stands and demanding
     * an explicit derogation with a reason for the rest.
     */
    ReleaseDto sign(UUID tenantId, UUID legId, SignReleaseCommand command);

    ReleaseDto acknowledge(UUID tenantId, UUID legId, AcknowledgeReleaseCommand command);
}
