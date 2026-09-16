package com.thenetworkplan.networkplan.techlog.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TechLogEntryDto(
        UUID id,
        UUID aircraftId,
        String registration,
        String icaoType,
        UUID legId,
        String pageRef,
        LocalDate flownOn,
        String depIcao,
        String arrIcao,
        Integer blockMinutes,
        Integer airMinutes,
        int cycles,
        BigDecimal fuelUpliftLitres,
        BigDecimal oilAddedLitres,
        String commanderName,
        String engineerName,
        String status,
        OffsetDateTime signedAt,
        String remark,
        List<DefectDto> defects) implements Serializable {
}
