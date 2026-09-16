package com.thenetworkplan.networkplan.roster.mapper;

import com.thenetworkplan.networkplan.roster.domain.RosterEntry;
import com.thenetworkplan.networkplan.roster.domain.RosterStatus;
import com.thenetworkplan.networkplan.roster.domain.RosterVersion;
import com.thenetworkplan.networkplan.roster.dto.RosterCellDto;
import com.thenetworkplan.networkplan.roster.dto.RosterVersionDto;
import org.springframework.stereotype.Component;

@Component
public class RosterMapper {

    /** @param entryCount counted by the repository, not by loading the cells */
    public RosterVersionDto toDto(RosterVersion version, long entryCount) {
        return new RosterVersionDto(
                version.getId(),
                version.getLabel(),
                version.getPeriodStart(),
                version.getPeriodEnd(),
                version.getStatus().name(),
                version.getPublishedAt(),
                version.getPublishedBy(),
                entryCount,
                version.getStatus().isEditable());
    }

    public RosterCellDto toDto(RosterEntry entry) {
        return new RosterCellDto(
                entry.getId(),
                entry.getDutyDate(),
                entry.getCode().name(),
                entry.getDutyPeriodId() != null,
                entry.getLegId(),
                entry.getRemark(),
                entry.getRosterVersion().getStatus() != RosterStatus.PUBLISHED);
    }
}
