package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.ops.service.OnTimePerformanceRule;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

/** See {@link OnTimePerformanceRule}. Reads one threshold, decides, returns. */
@Component
public class OnTimePerformanceRuleImpl implements OnTimePerformanceRule {

    private final OpsProperties opsProperties;

    public OnTimePerformanceRuleImpl(OpsProperties opsProperties) {
        this.opsProperties = opsProperties;
    }

    @Override
    public Measure measure(List<Departure> departures) {
        long tolerance = opsProperties.getDelayThreshold().toMinutes();
        int sample = 0;
        int onTime = 0;

        for (Departure departure : departures) {
            if (departure == null || departure.actual() == null || departure.scheduled() == null) {
                continue;
            }
            sample++;
            if (Duration.between(departure.scheduled(), departure.actual()).toMinutes() <= tolerance) {
                onTime++;
            }
        }

        if (sample == 0) {
            return Measure.NOTHING_DEPARTED;
        }
        return new Measure(Math.round((onTime * 100f) / sample), sample, onTime);
    }
}
