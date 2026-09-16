package com.thenetworkplan.networkplan.flightfollowing;

import static org.assertj.core.api.Assertions.assertThat;

import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule.Factor;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule.Level;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule.Scored;
import com.thenetworkplan.networkplan.flightfollowing.service.impl.SmsRiskRuleImpl;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The table the Flight Watch risk badges rest on.
 *
 * <p>The first four cases reproduce the prototype exactly, so the port can be
 * shown to be faithful. The last two cover the one behaviour we changed: an
 * unanswered source must not read as good news.
 */
class SmsRiskRuleTest {

    private final SmsRiskRule rule = new SmsRiskRuleImpl();

    private static Scored score(Factor factor, Level level) {
        return new Scored(factor, level, null);
    }

    @Test
    @DisplayName("nothing wrong is LOW, index 1")
    void allClear() {
        var assessment = rule.assess(List.of(
                score(Factor.WEATHER, Level.NONE),
                score(Factor.NOTAM, Level.NONE),
                score(Factor.FTL, Level.NONE),
                score(Factor.MEL, Level.NONE),
                score(Factor.CREW, Level.NONE)));

        assertThat(assessment.level()).isEqualTo("LOW");
        assertThat(assessment.index()).isEqualTo(1);
    }

    @Test
    @DisplayName("one major MEL alone is MEDIUM: severity 4, likelihood 2, index 8")
    void singleMajorMel() {
        var assessment = rule.assess(List.of(
                score(Factor.WEATHER, Level.NONE),
                score(Factor.NOTAM, Level.NONE),
                score(Factor.FTL, Level.NONE),
                score(Factor.MEL, Level.MAJOR),
                score(Factor.CREW, Level.NONE)));

        assertThat(assessment.severity()).isEqualTo(4);
        assertThat(assessment.likelihood()).isEqualTo(2);
        assertThat(assessment.index()).isEqualTo(8);
        assertThat(assessment.level()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("two concurrent factors raise the likelihood to 3")
    void twoFactors() {
        var assessment = rule.assess(List.of(
                score(Factor.WEATHER, Level.MODERATE),
                score(Factor.FTL, Level.MODERATE)));

        assertThat(assessment.likelihood()).isEqualTo(3);
        assertThat(assessment.index()).isEqualTo(9);
        assertThat(assessment.level()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("an FTL exceedance with three other live factors is CRITICAL")
    void critical() {
        var assessment = rule.assess(List.of(
                score(Factor.WEATHER, Level.MODERATE),
                score(Factor.NOTAM, Level.MINOR),
                score(Factor.FTL, Level.SEVERE),
                score(Factor.MEL, Level.MINOR),
                score(Factor.CREW, Level.NONE)));

        assertThat(assessment.severity()).isEqualTo(5);
        assertThat(assessment.likelihood()).isEqualTo(5);
        assertThat(assessment.index()).isEqualTo(25);
        assertThat(assessment.level()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("an unanswered source does not count as a clear one")
    void unknownIsNotClear() {
        var assessment = rule.assess(List.of(
                score(Factor.WEATHER, Level.UNKNOWN),
                score(Factor.NOTAM, Level.NONE),
                score(Factor.FTL, Level.NONE),
                score(Factor.MEL, Level.NONE),
                score(Factor.CREW, Level.NONE)));

        assertThat(assessment.unknown()).containsExactly(Factor.WEATHER);
        assertThat(assessment.action())
                .contains("Weather vs. minima")
                .contains("a floor, not a verdict");
    }

    @Test
    @DisplayName("an unanswered source never lowers a level reached by the others")
    void unknownDoesNotLower() {
        var withUnknown = rule.assess(List.of(
                score(Factor.WEATHER, Level.UNKNOWN),
                score(Factor.MEL, Level.MAJOR),
                score(Factor.FTL, Level.MODERATE)));
        var withoutUnknown = rule.assess(List.of(
                score(Factor.MEL, Level.MAJOR),
                score(Factor.FTL, Level.MODERATE)));

        assertThat(withUnknown.index()).isEqualTo(withoutUnknown.index());
        assertThat(withUnknown.level()).isEqualTo(withoutUnknown.level());
    }
}
