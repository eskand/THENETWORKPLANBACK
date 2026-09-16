package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * One OOOI time.
 *
 * <p>OUT, OFF, ON and IN are four distinct events. The prototype wrote the same
 * ATD value into off-block and airborne, which made its MVT messages
 * non-conformant and its block times unusable for the journey log.
 *
 * @param kind         OUT, OFF, ON or IN
 * @param at           the time, UTC
 * @param delayMinutes coded delay to record with the movement, when there is one
 */
public record RecordMovementCommand(
        @NotBlank String kind,
        @NotNull OffsetDateTime at,
        @Min(1) Integer delayMinutes,
        String delayCode,
        String remark) {
}
