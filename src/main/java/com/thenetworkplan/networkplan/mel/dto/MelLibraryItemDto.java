package com.thenetworkplan.networkplan.mel.dto;

import java.io.Serializable;
import java.util.UUID;

public record MelLibraryItemDto(
        UUID id,
        String icaoType,
        String itemRef,
        String ataChapter,
        String title,
        String melCategory,
        Integer rectificationDays,
        Integer installedQuantity,
        Integer requiredQuantity,
        boolean placardRequired,
        String operationalProcedure,
        String maintenanceProcedure,
        String limitation) implements Serializable {
}
