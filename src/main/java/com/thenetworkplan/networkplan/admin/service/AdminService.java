package com.thenetworkplan.networkplan.admin.service;

import com.thenetworkplan.networkplan.admin.dto.AdminDtos.AircraftReferenceDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.DatabaseBoardDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.FleetRegisterDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.ImportSettingsCommand;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingsFormDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.UpdateSettingCommand;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settings and Database — the two administration screens.
 *
 * <p>They share a package because they share a subject: what the operator has
 * declared, and what the product knows. Neither owns operational data.
 */
public interface AdminService {

    List<SettingDto> findSettings(UUID tenantId, String category);

    SettingDto updateSetting(UUID tenantId, UUID settingId, UpdateSettingCommand command, UUID actorId);

    /** Every rubric, group and field of the Settings screen, in one read. */
    SettingsFormDto findSettingsForm(UUID tenantId);

    /**
     * Puts a rubric back to its seeded values, or the whole configuration.
     *
     * @param sectionName null to reset everything
     * @return how many rows actually moved
     */
    int resetSettings(UUID tenantId, String sectionName, UUID actorId);

    /** Restores a configuration file. Unknown keys are ignored, never created. */
    int importSettings(UUID tenantId, Map<String, String> values, UUID actorId);

    /** The Fleet Register: every tail, grouped by family, with its type figures. */
    FleetRegisterDto findFleetRegister(UUID tenantId);

    /** The 308-type aircraft reference, searched on designator, manufacturer and model. */
    List<AircraftReferenceDto> findAircraftReference(String search, int limit);

    /** The reference sets, with their real row counts and their last change. */
    DatabaseBoardDto findDatabaseBoard(UUID tenantId);
}
