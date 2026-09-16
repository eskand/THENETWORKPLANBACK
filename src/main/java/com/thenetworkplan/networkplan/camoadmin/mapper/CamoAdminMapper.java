package com.thenetworkplan.networkplan.camoadmin.mapper;

import com.thenetworkplan.networkplan.camoadmin.domain.Directive;
import com.thenetworkplan.networkplan.camoadmin.domain.DirectiveApplication;
import com.thenetworkplan.networkplan.camoadmin.domain.DirectiveStatus;
import com.thenetworkplan.networkplan.camoadmin.domain.ProgrammeTask;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveApplicationDto;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveDto;
import com.thenetworkplan.networkplan.camoadmin.dto.ProgrammeTaskDto;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CamoAdminMapper {

    public ProgrammeTaskDto toDto(ProgrammeTask task, int appliedToAircraft) {
        return new ProgrammeTaskDto(
                task.getId(),
                task.getAircraftType().getIcaoType(),
                task.getCode(),
                task.getTitle(),
                task.getAtaChapter(),
                task.getIntervalHours(),
                task.getIntervalCycles(),
                task.getIntervalMonths(),
                task.getToleranceHours(),
                task.getToleranceDays(),
                task.isMandatory(),
                task.getReference(),
                appliedToAircraft);
    }

    /**
     * @param applications the per-tail rows, already loaded; the three counts and
     *                     the status are derived from them rather than from a
     *                     second query
     */
    public DirectiveDto toDto(Directive directive, List<DirectiveApplicationDto> applications, LocalDate today) {
        int complied = (int) applications.stream()
                .filter(application -> DirectiveStatus.COMPLIED.name().equals(application.status()))
                .count();
        int outstanding = (int) applications.stream()
                .filter(application -> DirectiveStatus.OPEN.name().equals(application.status())
                        || DirectiveStatus.DEFERRED.name().equals(application.status()))
                .count();

        String status;
        if (outstanding == 0) {
            status = applications.isEmpty() ? "NOT_APPLICABLE" : "COMPLIED";
        } else if (directive.getComplianceByDate() != null && directive.getComplianceByDate().isBefore(today)) {
            status = "OVERDUE";
        } else {
            status = "OPEN";
        }

        return new DirectiveDto(
                directive.getId(),
                directive.getKind().name(),
                directive.getReference(),
                directive.getSubject(),
                directive.getIssuedBy(),
                directive.getIssuedOn(),
                directive.getEffectiveOn(),
                directive.getAircraftType() == null ? null : directive.getAircraftType().getIcaoType(),
                directive.getComplianceByDate(),
                directive.getComplianceByHours(),
                directive.getMethod(),
                directive.getRecurringMonths(),
                applications.size(),
                complied,
                outstanding,
                status,
                applications);
    }

    public DirectiveApplicationDto toDto(DirectiveApplication application) {
        return new DirectiveApplicationDto(
                application.getId(),
                application.getAircraft().getId(),
                application.getAircraft().getRegistration(),
                application.getStatus().name(),
                application.getCompliedOn(),
                application.getCompliedRef(),
                application.getRemark());
    }
}
