package com.thenetworkplan.networkplan.mel.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.MelCategory;
import com.thenetworkplan.networkplan.mel.service.MelRectificationRule;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * Part-MEL default intervals, overridden by the operator line when it has one.
 *
 * <p>Category A carries no default: the interval is written in the MEL line
 * itself, and when the line is silent the answer is null — the item then shows
 * as "interval per MEL remark" instead of borrowing category B's three days.
 */
@Component
public class MelRectificationRuleImpl implements MelRectificationRule {

    @Override
    public OffsetDateTime dueAt(MelCategory category, Integer libraryDays, OffsetDateTime raisedAt) {
        if (raisedAt == null) {
            return null;
        }
        Integer days = libraryDays != null ? libraryDays : defaultDays(category);
        return days == null ? null : raisedAt.plusDays(days);
    }

    private Integer defaultDays(MelCategory category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case A -> null;
            case B -> 3;
            case C -> 10;
            case D -> 120;
        };
    }
}
