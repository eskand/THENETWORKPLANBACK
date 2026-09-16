package com.thenetworkplan.networkplan.common.exception;

import java.io.Serial;

/** The caller asked for something that does not exist in this tenant. */
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Object key) {
        return new ResourceNotFoundException(what + " " + key + " not found");
    }
}
