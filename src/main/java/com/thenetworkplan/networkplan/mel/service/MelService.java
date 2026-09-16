package com.thenetworkplan.networkplan.mel.service;

import com.thenetworkplan.networkplan.mel.dto.CloseMelCommand;
import com.thenetworkplan.networkplan.mel.dto.MelEntryDto;
import com.thenetworkplan.networkplan.mel.dto.MelLibraryItemDto;
import com.thenetworkplan.networkplan.mel.dto.RaiseMelCommand;
import java.util.List;
import java.util.UUID;

/**
 * MEL / CDL / HIL.
 *
 * <p>Owns the operator library ({@code camo.mel_library}) and the life cycle of
 * a deferral. The deferred items themselves are {@code camo.mel_items}, which
 * DOM5 already exposes to dispatch — this module is what raises and clears
 * them, so there is one writer and one reader of the same table.
 */
public interface MelService {

    List<MelLibraryItemDto> findLibrary(UUID tenantId, String icaoType, String ataChapter);

    /** Every open deferral of the fleet, worst due status first. */
    List<MelEntryDto> findOpen(UUID tenantId);

    List<MelEntryDto> findForAircraft(UUID tenantId, UUID aircraftId, boolean openOnly);

    MelEntryDto raise(UUID tenantId, RaiseMelCommand command);

    MelEntryDto close(UUID tenantId, UUID melItemId, CloseMelCommand command);
}
