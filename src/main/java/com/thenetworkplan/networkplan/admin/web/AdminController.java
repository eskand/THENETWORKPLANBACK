package com.thenetworkplan.networkplan.admin.web;

import com.thenetworkplan.networkplan.admin.dto.AdminDtos.AircraftReferenceDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.DatabaseBoardDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.FleetRegisterDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.ImportSettingsCommand;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingsFormDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.UpdateSettingCommand;
import com.thenetworkplan.networkplan.admin.service.AdminService;
import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API72 / API73 — Settings and Database. */
@RestController
@RequestMapping("/v1")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/settings")
    public List<SettingDto> settings(@RequestParam(name = "category", required = false) String category) {
        return adminService.findSettings(TenantContext.require(), category);
    }

    @PatchMapping("/settings/{id}")
    public SettingDto updateSetting(@PathVariable UUID id,
                                    @Valid @RequestBody UpdateSettingCommand command,
                                    @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return adminService.updateSetting(TenantContext.require(), id, command, actorId);
    }

    /** The Settings screen: rubrics, groups and fields with their controls. */
    @GetMapping("/settings/form")
    public SettingsFormDto settingsForm() {
        return adminService.findSettingsForm(TenantContext.require());
    }

    /**
     * @param section the rubric to restore; omit to reset the whole configuration
     */
    @PostMapping("/settings/reset")
    public Map<String, Integer> resetSettings(
            @RequestParam(name = "section", required = false) String section,
            @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return Map.of("reset", adminService.resetSettings(TenantContext.require(), section, actorId));
    }

    @PostMapping("/settings/import")
    public Map<String, Integer> importSettings(
            @RequestBody ImportSettingsCommand command,
            @RequestHeader(name = "X-Actor-Id", required = false) UUID actorId) {
        return Map.of("changed",
                adminService.importSettings(TenantContext.require(), command.values(), actorId));
    }

    /** The Fleet Register tab of the Database screen. */
    @GetMapping("/database/fleet-register")
    public FleetRegisterDto fleetRegister() {
        return adminService.findFleetRegister(TenantContext.require());
    }

    @GetMapping("/database/aircraft-reference")
    public List<AircraftReferenceDto> aircraftReference(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return adminService.findAircraftReference(search, limit);
    }

    @GetMapping("/database/reference-sets")
    public DatabaseBoardDto referenceSets() {
        return adminService.findDatabaseBoard(TenantContext.require());
    }
}
