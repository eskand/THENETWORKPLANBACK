package com.thenetworkplan.networkplan.dispatch.service;

import com.thenetworkplan.networkplan.dispatch.dto.DispatchBoardDto;
import com.thenetworkplan.networkplan.dispatch.dto.DispatchFilter;
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
}
