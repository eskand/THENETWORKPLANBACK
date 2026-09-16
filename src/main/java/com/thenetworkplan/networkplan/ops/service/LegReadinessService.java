package com.thenetworkplan.networkplan.ops.service;

import com.thenetworkplan.networkplan.ops.dto.ReadinessDto;
import java.util.UUID;

/**
 * API3 — what is missing before a leg can be released.
 *
 * <p>Separate from {@link ReleaseService} because the board reads it on every
 * refresh while nothing is being signed, and separate from the leg because it
 * spans four domains (airworthiness, crew, trip support, reference data).
 */
public interface LegReadinessService {

    ReadinessDto assess(UUID tenantId, UUID legId);
}
