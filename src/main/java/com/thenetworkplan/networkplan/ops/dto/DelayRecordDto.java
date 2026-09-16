package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;
import java.util.UUID;

public record DelayRecordDto(
        UUID id,
        UUID legId,
        int minutes,
        String code,
        String subCode,
        String remark) implements Serializable {
}
