package com.thenetworkplan.networkplan.techlog.mapper;

import com.thenetworkplan.networkplan.techlog.domain.Defect;
import com.thenetworkplan.networkplan.techlog.domain.TechLogEntry;
import com.thenetworkplan.networkplan.techlog.dto.DefectDto;
import com.thenetworkplan.networkplan.techlog.dto.TechLogEntryDto;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TechLogMapper {

    /** @param defects already loaded for this page by the service, in one query */
    public TechLogEntryDto toDto(TechLogEntry entry, List<DefectDto> defects) {
        return new TechLogEntryDto(
                entry.getId(),
                entry.getAircraft().getId(),
                entry.getAircraft().getRegistration(),
                entry.getAircraft().getAircraftType() == null
                        ? null
                        : entry.getAircraft().getAircraftType().getIcaoType(),
                entry.getLegId(),
                entry.getPageRef(),
                entry.getFlownOn(),
                entry.getDepIcao(),
                entry.getArrIcao(),
                entry.getBlockMinutes(),
                entry.getAirMinutes(),
                entry.getCycles(),
                entry.getFuelUpliftLitres(),
                entry.getOilAddedLitres(),
                entry.getCommander() == null ? null : entry.getCommander().fullName(),
                entry.getEngineer() == null ? null : entry.getEngineer().fullName(),
                entry.getStatus().name(),
                entry.getSignedAt(),
                entry.getRemark(),
                defects);
    }

    public DefectDto toDto(Defect defect) {
        return new DefectDto(
                defect.getId(),
                defect.getAircraft().getId(),
                defect.getAircraft().getRegistration(),
                defect.getTechLogEntry() == null ? null : defect.getTechLogEntry().getId(),
                defect.getAtaChapter(),
                defect.getDescription(),
                defect.getReportedAt(),
                defect.getReportedBy() == null ? null : defect.getReportedBy().fullName(),
                defect.getStatus().name(),
                defect.getMelItemId(),
                defect.getCorrectiveAction(),
                defect.getClosedAt());
    }
}
