package com.thenetworkplan.networkplan.ops.dto;

import java.util.UUID;

/**
 * Signing the dispatch release.
 *
 * <p>A derogation is the only way past a derogable finding, and it demands a
 * reason. Blocking findings cannot be derogated at all.
 */
public record SignReleaseCommand(
        UUID signedBy,
        boolean derogation,
        String derogationReason) {
}
