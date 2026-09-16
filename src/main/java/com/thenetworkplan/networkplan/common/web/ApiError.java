package com.thenetworkplan.networkplan.common.web;

import java.time.OffsetDateTime;
import java.util.List;

/** Error payload returned by every endpoint. */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String rule,
        String path,
        List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(OffsetDateTime.now(), status, error, message, null, path, null);
    }
}
