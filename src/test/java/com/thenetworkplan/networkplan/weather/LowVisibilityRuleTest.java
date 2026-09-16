package com.thenetworkplan.networkplan.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;
import com.thenetworkplan.networkplan.weather.service.LowVisibilityRule;
import com.thenetworkplan.networkplan.weather.service.LowVisibilityRule.Verdict;
import com.thenetworkplan.networkplan.weather.service.impl.LowVisibilityRuleImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The table the Status Board's low visibility tile rests on.
 *
 * <p>The interesting cases are the two that are not "bad weather": a message
 * that says nothing about visibility must not read as clear, and CAVOK must
 * not read as unknown for want of a cloud group.
 */
class LowVisibilityRuleTest {

    private static final int VIS = 1500;
    private static final int CEILING = 300;

    private final LowVisibilityRule rule = new LowVisibilityRuleImpl();

    private static ObservationDto observation(Integer visibilityM, boolean cavok, Integer ceilingFt) {
        return new ObservationDto("METAR", null, null, 0, "", "NOAA",
                null, false, null, null, visibilityM, cavok, ceilingFt,
                null, null, null, null, null);
    }

    @Test
    @DisplayName("no observation at all cannot be assessed")
    void noObservation() {
        assertThat(rule.assess(null, VIS, CEILING)).isEqualTo(Verdict.NOT_ASSESSABLE);
    }

    @Test
    @DisplayName("CAVOK answers the question on its own")
    void cavok() {
        assertThat(rule.assess(observation(9999, true, null), VIS, CEILING)).isEqualTo(Verdict.ABOVE);
    }

    @Test
    @DisplayName("a message with neither visibility nor ceiling is not clear, it is unknown")
    void silentMessage() {
        assertThat(rule.assess(observation(null, false, null), VIS, CEILING))
                .isEqualTo(Verdict.NOT_ASSESSABLE);
    }

    @Test
    @DisplayName("visibility at the minima is below, not above")
    void visibilityAtMinima() {
        assertThat(rule.assess(observation(1500, false, 2000), VIS, CEILING)).isEqualTo(Verdict.BELOW);
    }

    @Test
    @DisplayName("a 200 ft ceiling triggers LVP even with ten kilometres of visibility")
    void lowCeiling() {
        assertThat(rule.assess(observation(9999, false, 200), VIS, CEILING)).isEqualTo(Verdict.BELOW);
    }

    @Test
    @DisplayName("an ordinary summer day is above the minima")
    void ordinaryDay() {
        assertThat(rule.assess(observation(9999, false, 2600), VIS, CEILING)).isEqualTo(Verdict.ABOVE);
    }

    @Test
    @DisplayName("visibility known and low decides even when the ceiling is missing")
    void fogWithoutCloudGroup() {
        assertThat(rule.assess(observation(300, false, null), VIS, CEILING)).isEqualTo(Verdict.BELOW);
    }
}
