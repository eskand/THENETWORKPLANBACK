package com.thenetworkplan.networkplan.crew.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The last time one person came off duty, answered by the database.
 *
 * <p>Same device as {@link PersonMinutes}: one {@code group by} for the whole
 * crew instead of one query per candidate on the scheduling pool.
 */
public record PersonInstant(UUID personId, OffsetDateTime instant) {
}
