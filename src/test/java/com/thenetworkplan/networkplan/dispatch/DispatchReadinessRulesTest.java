package com.thenetworkplan.networkplan.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchFilter;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchTab;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceReadiness;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DispatchReadinessRulesTest {

    @Test
    @DisplayName("services are READY only when every request is confirmed")
    void serviceReadiness() {
        assertEquals(ServiceReadiness.READY, ServiceReadiness.of(4, 4, false));
        assertEquals(ServiceReadiness.PENDING, ServiceReadiness.of(4, 3, false));
        assertEquals(ServiceReadiness.ATTENTION, ServiceReadiness.of(0, 0, false));
        assertEquals(ServiceReadiness.ATTENTION, ServiceReadiness.of(4, 4, true));
    }

    @Test
    @DisplayName("a leg with no assignment blocks, and says so")
    void unassignedCrewBlocks() {
        LegCrewDto crew = LegCrewDto.unassigned(UUID.randomUUID(), 2);
        assertFalse(crew.complete());
        assertTrue(crew.blocking());
        assertEquals("UNKNOWN", crew.ftlStatus());
    }

    @Test
    @DisplayName("an unknown tab falls back to All Flights instead of failing")
    void tabParsing() {
        assertEquals(DispatchTab.NEEDS_ACTION, DispatchTab.parse("needs-action"));
        assertEquals(DispatchTab.AOG_MAINTENANCE, DispatchTab.parse("AOG_MAINTENANCE"));
        assertEquals(DispatchTab.ALL_FLIGHTS, DispatchTab.parse("whatever"));
        assertEquals(DispatchTab.ALL_FLIGHTS, DispatchTab.parse(null));
    }

    @Test
    @DisplayName("ALL and blank selectors mean no restriction, and the cache key says so")
    void filterNormalisation() {
        DispatchFilter filter = DispatchFilter.of(LocalDate.of(2026, 9, 9), "scheduled", "  ", "all");
        assertEquals(DispatchTab.SCHEDULED, filter.tab());
        assertEquals(null, filter.fleetType());
        assertEquals(null, filter.baseIcao());
        assertEquals("2026-09-09|SCHEDULED|*|*", filter.cacheKey());
    }

    @Test
    @DisplayName("selectors are upper-cased so the cache key is stable")
    void filterCacheKeyIsStable() {
        DispatchFilter one = DispatchFilter.of(LocalDate.of(2026, 9, 9), "all_flights", "f2th", "dtta");
        DispatchFilter two = DispatchFilter.of(LocalDate.of(2026, 9, 9), "ALL_FLIGHTS", "F2TH", "DTTA");
        assertEquals(one.cacheKey(), two.cacheKey());
    }
}
