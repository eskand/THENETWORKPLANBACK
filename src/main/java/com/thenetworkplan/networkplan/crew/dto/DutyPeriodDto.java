package com.thenetworkplan.networkplan.crew.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DutyPeriodDto(
        UUID id,
        UUID personId,
        UUID legId,
        String kind,
        String rosterCode,
        OffsetDateTime reportAt,
        OffsetDateTime offDutyAt,
        long dutyMinutes,
        Integer blockMinutes,
        int sectors,
        String remark) implements Serializable {
}
