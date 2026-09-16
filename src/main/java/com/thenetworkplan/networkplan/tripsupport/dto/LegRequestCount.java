package com.thenetworkplan.networkplan.tripsupport.dto;

import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import java.util.UUID;

/**
 * One row of a {@code group by leg_id, status} aggregate.
 *
 * <p>Used as a JPQL constructor expression so counting the services of a whole
 * day costs one grouped query instead of loading every request row.
 */
public record LegRequestCount(UUID legId, RequestStatus status, Long count) {
}
