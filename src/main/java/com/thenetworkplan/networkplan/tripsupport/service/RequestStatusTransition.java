package com.thenetworkplan.networkplan.tripsupport.service;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The single place where the life cycle of a permit or service request is
 * enforced, shared by both services rather than duplicated in each.
 *
 * <p>Refusing a transition is the point: the prototype let a request be marked
 * "requested" with no message sent and no way back.
 */
@Component
public class RequestStatusTransition {

    private static final Map<RequestStatus, Set<RequestStatus>> ALLOWED = new EnumMap<>(RequestStatus.class);

    static {
        ALLOWED.put(RequestStatus.DRAFT, EnumSet.of(RequestStatus.SENT));
        ALLOWED.put(RequestStatus.SENT,
                EnumSet.of(RequestStatus.ACKNOWLEDGED, RequestStatus.CONFIRMED, RequestStatus.REFUSED));
        ALLOWED.put(RequestStatus.ACKNOWLEDGED,
                EnumSet.of(RequestStatus.CONFIRMED, RequestStatus.REFUSED));
        ALLOWED.put(RequestStatus.CONFIRMED, EnumSet.of(RequestStatus.REFUSED));
        ALLOWED.put(RequestStatus.REFUSED, EnumSet.of(RequestStatus.SENT));
    }

    public RequestStatus parse(String raw) {
        try {
            return RequestStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("REQUEST_STATUS_UNKNOWN", "Unknown request status: " + raw);
        }
    }

    public void check(RequestStatus from, RequestStatus to, String reference) {
        if (from == to) {
            return;
        }
        if (!ALLOWED.getOrDefault(from, EnumSet.noneOf(RequestStatus.class)).contains(to)) {
            throw new BusinessRuleException("REQUEST_TRANSITION_REFUSED",
                    "A request cannot go from " + from + " to " + to);
        }
        if (to == RequestStatus.CONFIRMED && (reference == null || reference.isBlank())) {
            throw new BusinessRuleException("REQUEST_REFERENCE_REQUIRED",
                    "A confirmation must carry the reference given by the recipient");
        }
    }
}
