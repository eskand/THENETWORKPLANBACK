package com.thenetworkplan.networkplan.weather.service.impl;

import com.thenetworkplan.networkplan.weather.dto.WeatherDtos.ObservationDto;
import com.thenetworkplan.networkplan.weather.service.LowVisibilityRule;
import org.springframework.stereotype.Component;

/** See {@link LowVisibilityRule}. Nine lines of decision, and no state. */
@Component
public class LowVisibilityRuleImpl implements LowVisibilityRule {

    @Override
    public Verdict assess(ObservationDto observation, int visibilityMinimaM, int ceilingMinimaFt) {
        if (observation == null) {
            return Verdict.NOT_ASSESSABLE;
        }
        // CAVOK means visibility 10 km or more and no cloud below 5000 ft:
        // the message has already answered the question.
        if (observation.cavok()) {
            return Verdict.ABOVE;
        }

        Integer visibility = observation.visibilityM();
        Integer ceiling = observation.ceilingFt();
        if (visibility == null && ceiling == null) {
            return Verdict.NOT_ASSESSABLE;
        }
        if (visibility != null && visibility <= visibilityMinimaM) {
            return Verdict.BELOW;
        }
        if (ceiling != null && ceiling <= ceilingMinimaFt) {
            return Verdict.BELOW;
        }
        return Verdict.ABOVE;
    }
}
