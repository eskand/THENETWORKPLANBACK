package com.thenetworkplan.networkplan.techlog.dto;

import java.util.UUID;

/**
 * Closing a defect, or deferring it under a MEL line.
 *
 * <p>Exactly one of the two paths: a corrective action closes it, a library
 * item defers it. Asking for both, or neither, is refused — that is the choice
 * an engineer actually makes, and leaving it implicit is how defects went
 * missing in the prototype.
 */
public record ResolveDefectCommand(
        String correctiveAction,
        UUID melLibraryItemId,
        Boolean placardFitted,
        String remark,
        UUID actorId) {
}
