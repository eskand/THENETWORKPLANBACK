package com.thenetworkplan.networkplan.crew.dto;

import java.util.UUID;

/**
 * Minutes accumulated by one person over one window, counted by the database.
 *
 * <p>Same device as {@code LegRequestCount} on the dispatch board: a
 * {@code group by} aggregate returned through a JPQL constructor expression, so
 * the rows are counted in PostgreSQL instead of being shipped to the JVM to be
 * counted there.
 */
public record PersonMinutes(UUID personId, Long minutes) {

    public long minutesOrZero() {
        return minutes == null ? 0L : minutes;
    }
}
