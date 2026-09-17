package com.thenetworkplan.networkplan.dispatch.service;

import com.thenetworkplan.networkplan.dispatch.dto.DispatchBoardDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchFilter;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchRowDto;
import java.util.UUID;

/**
 * The read model behind the Dispatch screen.
 *
 * <p>It owns no table. Its job is to ask five domains the one question each can
 * answer well, and to assemble the answers into the picture an OCC reads — which
 * is why it lives in its own module rather than inside DOM1.
 */
public interface DispatchBoardService {

    DispatchBoardDto load(UUID tenantId, DispatchFilter filter);

    /**
     * One leg, assembled exactly as the board assembles its rows.
     *
     * <p>It exists because the flight file is ONE screen opened from two
     * places — the Dispatch board and the Flight Timeline — and the annexe
     * opens the same panel from both (prototype l. 9389: one
     * {@code showDetail()}, two hosts). Two screens reading two different
     * shapes is how they start disagreeing: the board saying « Ready » while
     * the timeline says « crew incomplete » on the same leg. The panel
     * therefore reads one shape, and this is it.
     */
    DispatchRowDto findRow(UUID tenantId, UUID legId);
}
