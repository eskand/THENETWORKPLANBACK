package com.thenetworkplan.networkplan.ops.domain;

/**
 * Alert ranking (FR15).
 *
 * <p>The audit's complaint was that the prototype had no unified alert wall:
 * toasts, bell scans and FTL popups accumulated with no queue and no
 * acknowledgement. Severity plus an acknowledgement column is the minimum that
 * makes an alert actionable.
 */
public enum AlertSeverity {
    INFO,
    ATTENTION,
    CRITICAL
}
