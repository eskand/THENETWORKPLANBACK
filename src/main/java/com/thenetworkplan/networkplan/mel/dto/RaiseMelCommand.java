package com.thenetworkplan.networkplan.mel.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Deferring a defect under a MEL line.
 *
 * <p>No due date and no category in the command: both come from the library
 * line, through {@code MelRectificationRule}. A caller that could choose its own
 * interval could defer a category B item for four months.
 */
public record RaiseMelCommand(
        @NotNull UUID aircraftId,
        @NotNull UUID melLibraryItemId,
        UUID defectId,
        String remark,
        Boolean placardFitted,
        UUID raisedBy) {
}
