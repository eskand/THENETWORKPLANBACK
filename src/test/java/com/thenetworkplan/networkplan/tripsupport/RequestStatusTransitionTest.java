package com.thenetworkplan.networkplan.tripsupport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import com.thenetworkplan.networkplan.tripsupport.service.RequestStatusTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RequestStatusTransitionTest {

    private final RequestStatusTransition transition = new RequestStatusTransition();

    @Test
    @DisplayName("a draft can only be sent")
    void draftGoesToSent() {
        assertDoesNotThrow(() -> transition.check(RequestStatus.DRAFT, RequestStatus.SENT, null));
        assertThrows(BusinessRuleException.class,
                () -> transition.check(RequestStatus.DRAFT, RequestStatus.CONFIRMED, "REF-1"));
    }

    @Test
    @DisplayName("a confirmation without a reference is refused")
    void confirmationNeedsAReference() {
        BusinessRuleException thrown = assertThrows(BusinessRuleException.class,
                () -> transition.check(RequestStatus.SENT, RequestStatus.CONFIRMED, "  "));
        assertEquals("REQUEST_REFERENCE_REQUIRED", thrown.getRule());
    }

    @Test
    @DisplayName("a confirmation with a reference is accepted")
    void confirmationWithReference() {
        assertDoesNotThrow(
                () -> transition.check(RequestStatus.SENT, RequestStatus.CONFIRMED, "NG/LDG/120"));
    }

    @Test
    @DisplayName("re-setting the same status is a no-op, not an error")
    void sameStatusIsAllowed() {
        assertDoesNotThrow(() -> transition.check(RequestStatus.SENT, RequestStatus.SENT, null));
    }

    @Test
    @DisplayName("an unknown status name is refused with its rule")
    void unknownStatus() {
        BusinessRuleException thrown =
                assertThrows(BusinessRuleException.class, () -> transition.parse("requested"));
        assertEquals("REQUEST_STATUS_UNKNOWN", thrown.getRule());
    }
}
