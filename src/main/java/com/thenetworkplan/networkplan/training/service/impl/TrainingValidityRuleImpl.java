package com.thenetworkplan.networkplan.training.service.impl;

import com.thenetworkplan.networkplan.training.domain.TrainingCourse;
import com.thenetworkplan.networkplan.training.service.TrainingValidityRule;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Day-for-day validity: a twelve-month course completed on 09 SEP 2026 lapses
 * on 09 SEP 2027.
 *
 * <p>EASA allows the "same calendar month" convention, which would push it to
 * 30 SEP 2027. That convention is the operator's to declare, and declaring it
 * means replacing this bean — not scattering a {@code plusMonths} in three
 * services, which is how the prototype ended up with several answers.
 */
@Component
public class TrainingValidityRuleImpl implements TrainingValidityRule {

    @Override
    public LocalDate validUntil(TrainingCourse course, LocalDate completedOn) {
        if (course.getValidityMonths() == null || completedOn == null) {
            return null;
        }
        return completedOn.plusMonths(course.getValidityMonths());
    }
}
