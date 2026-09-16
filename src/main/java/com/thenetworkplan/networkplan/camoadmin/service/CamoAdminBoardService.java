package com.thenetworkplan.networkplan.camoadmin.service;

import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.AuditEventDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.CamoAdminBoardDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.ComponentDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.DocumentDto;
import com.thenetworkplan.networkplan.camoadmin.dto.CamoAdminDtos.UserDto;
import java.util.List;
import java.util.UUID;

/**
 * The CAMO back office.
 *
 * <p>The dashboard and its alerts are computed, never stored. An ARC that
 * expired overnight has to be on the screen this morning without anyone having
 * written a row to say so — which is exactly what a stored alert table cannot
 * promise.
 */
public interface CamoAdminBoardService {

    CamoAdminBoardDto findBoard(UUID tenantId);

    /** The component register. {@code category} null returns all four. */
    List<ComponentDto> findComponents(UUID tenantId, String category);

    List<DocumentDto> findDocuments(UUID tenantId, String category);

    /** Who may change the record, and who merely reads it. */
    List<UserDto> findUsers(UUID tenantId);

    /** The audit trail, newest first. */
    List<AuditEventDto> findAuditTrail(UUID tenantId, int limit);
}
