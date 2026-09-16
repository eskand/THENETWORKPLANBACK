package com.thenetworkplan.networkplan.camo.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

public record UtilisationRowDto(
        UUID id,
        UUID legId,
        LocalDate flownOn,
        int blockMinutes,
        Integer airMinutes,
        int cycles,
        String sourceType,
        String sourceRef) implements Serializable {
}
