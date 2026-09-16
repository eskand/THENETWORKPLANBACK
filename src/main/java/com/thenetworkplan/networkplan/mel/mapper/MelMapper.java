package com.thenetworkplan.networkplan.mel.mapper;

import com.thenetworkplan.networkplan.airworthiness.domain.MelItem;
import com.thenetworkplan.networkplan.mel.domain.MelLibraryItem;
import com.thenetworkplan.networkplan.mel.dto.MelEntryDto;
import com.thenetworkplan.networkplan.mel.dto.MelLibraryItemDto;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class MelMapper {

    public MelLibraryItemDto toDto(MelLibraryItem item) {
        return new MelLibraryItemDto(
                item.getId(),
                item.getAircraftType() == null ? null : item.getAircraftType().getIcaoType(),
                item.getItemRef(),
                item.getAtaChapter(),
                item.getTitle(),
                item.getMelCategory().name(),
                item.getRectificationDays(),
                item.getInstalledQuantity(),
                item.getRequiredQuantity(),
                item.isPlacardRequired(),
                item.getOperationalProcedure(),
                item.getMaintenanceProcedure(),
                item.getLimitation());
    }

    /**
     * @param library the operator line behind the deferral, or null when the
     *                item predates the library — the screen then shows what is
     *                stored and nothing more
     */
    public MelEntryDto toDto(MelItem item, MelLibraryItem library, OffsetDateTime now) {
        return toDto(item, library, now, null, null);
    }

    /**
     * @param defectAta the ATA chapter of the tech log defect that raised this
     *                  deferral, or null when none did
     * @param source    "Tech Log" or "CAMO" — how the line reached the list
     */
    public MelEntryDto toDto(MelItem item, MelLibraryItem library, OffsetDateTime now,
                             String defectAta, String source) {
        Long daysRemaining = item.getDueAt() == null
                ? null
                : Duration.between(now, item.getDueAt()).toDays();

        String dueStatus;
        if (item.getDueAt() == null) {
            // No interval is not "plenty of time": it is unknown, and it says so.
            dueStatus = "UNKNOWN";
        } else if (daysRemaining < 0) {
            dueStatus = "OVERDUE";
        } else if (daysRemaining <= 2) {
            dueStatus = "DUE_SOON";
        } else {
            dueStatus = "PLANNED";
        }

        return new MelEntryDto(
                item.getId(),
                item.getAircraft().getId(),
                item.getAircraft().getRegistration(),
                item.getAircraft().getAircraftType() == null
                        ? null
                        : item.getAircraft().getAircraftType().getIcaoType(),
                item.getReference(),
                defectAta != null ? defectAta
                        : (library == null ? null : library.getAtaChapter()),
                library == null ? null : library.getTitle(),
                item.getMelCategory().name(),
                item.getTitle(),
                item.getLimitation(),
                item.getRaisedAt(),
                item.getDueAt(),
                daysRemaining,
                dueStatus,
                item.isBlocksDispatch(),
                library != null && library.isPlacardRequired(),
                item.isPlacardFitted(),
                library == null ? null : library.getOperationalProcedure(),
                source == null ? "CAMO" : source);
    }
}
