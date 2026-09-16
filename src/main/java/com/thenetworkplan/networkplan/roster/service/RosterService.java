package com.thenetworkplan.networkplan.roster.service;

import com.thenetworkplan.networkplan.roster.dto.CreateRosterVersionCommand;
import com.thenetworkplan.networkplan.roster.dto.RosterCellDto;
import com.thenetworkplan.networkplan.roster.dto.RosterGridDto;
import com.thenetworkplan.networkplan.roster.dto.RosterMonthDto;
import com.thenetworkplan.networkplan.roster.dto.RosterVersionDto;
import com.thenetworkplan.networkplan.roster.dto.SaveRosterEntryCommand;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** DOM4 — roster versions and the published grid. */
public interface RosterService {

    List<RosterVersionDto> findVersions(UUID tenantId);

    /** The grid of a version, or of the version published for today when null. */
    RosterGridDto findGrid(UUID tenantId, UUID versionId);

    /** One calendar month, assembled from every version that covers it. */
    RosterMonthDto findMonth(UUID tenantId, YearMonth month);

    RosterVersionDto create(UUID tenantId, CreateRosterVersionCommand command);

    /** Publishing freezes the version: no cell can be written afterwards. */
    RosterVersionDto publish(UUID tenantId, UUID versionId, UUID actorId);

    RosterCellDto saveEntry(UUID tenantId, UUID versionId, SaveRosterEntryCommand command);

    void removeEntry(UUID tenantId, UUID entryId);
}
