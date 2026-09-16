package com.thenetworkplan.networkplan.camoadmin.service.impl;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.camoadmin.domain.Directive;
import com.thenetworkplan.networkplan.camoadmin.domain.DirectiveApplication;
import com.thenetworkplan.networkplan.camoadmin.domain.DirectiveKind;
import com.thenetworkplan.networkplan.camoadmin.domain.DirectiveStatus;
import com.thenetworkplan.networkplan.camoadmin.domain.ProgrammeTask;
import com.thenetworkplan.networkplan.camoadmin.dto.AircraftCount;
import com.thenetworkplan.networkplan.camoadmin.dto.ComplyDirectiveCommand;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveApplicationDto;
import com.thenetworkplan.networkplan.camoadmin.dto.DirectiveDto;
import com.thenetworkplan.networkplan.camoadmin.dto.ProgrammeTaskDto;
import com.thenetworkplan.networkplan.camoadmin.dto.SaveDirectiveCommand;
import com.thenetworkplan.networkplan.camoadmin.dto.SaveProgrammeTaskCommand;
import com.thenetworkplan.networkplan.camoadmin.mapper.CamoAdminMapper;
import com.thenetworkplan.networkplan.camoadmin.repository.DirectiveApplicationRepository;
import com.thenetworkplan.networkplan.camoadmin.repository.DirectiveRepository;
import com.thenetworkplan.networkplan.camoadmin.repository.ProgrammeTaskRepository;
import com.thenetworkplan.networkplan.camoadmin.service.CamoAdminService;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import com.thenetworkplan.networkplan.refdata.repository.AircraftTypeRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CAMO Admin.
 *
 * <p>Two things live here: the Part-M programme, and the directives with what
 * each registration did about them. Registering a directive immediately creates
 * one application row per affected tail — a directive that does not say which
 * aircraft it hits cannot be worked.
 */
@Service
@Transactional(readOnly = true)
public class CamoAdminServiceImpl implements CamoAdminService {

    private final ProgrammeTaskRepository programmeTaskRepository;
    private final DirectiveRepository directiveRepository;
    private final DirectiveApplicationRepository applicationRepository;
    private final AircraftRepository aircraftRepository;
    private final AircraftTypeRepository aircraftTypeRepository;
    private final CamoAdminMapper mapper;

    public CamoAdminServiceImpl(ProgrammeTaskRepository programmeTaskRepository,
                                DirectiveRepository directiveRepository,
                                DirectiveApplicationRepository applicationRepository,
                                AircraftRepository aircraftRepository,
                                AircraftTypeRepository aircraftTypeRepository,
                                CamoAdminMapper mapper) {
        this.programmeTaskRepository = programmeTaskRepository;
        this.directiveRepository = directiveRepository;
        this.applicationRepository = applicationRepository;
        this.aircraftRepository = aircraftRepository;
        this.aircraftTypeRepository = aircraftTypeRepository;
        this.mapper = mapper;
    }

    @Override
    public List<ProgrammeTaskDto> findProgramme(UUID tenantId, String icaoType) {
        String type = (icaoType == null || icaoType.isBlank()) ? null : icaoType.trim().toUpperCase();
        // How many registrations exist per type, so a programme line can say how
        // many tails it reaches without a query per line.
        Map<String, Integer> fleetByType = aircraftRepository.findFleet(tenantId).stream()
                .collect(Collectors.groupingBy(
                        aircraft -> aircraft.getAircraftType().getIcaoType(),
                        Collectors.reducing(0, entry -> 1, Integer::sum)));

        return programmeTaskRepository.findProgramme(tenantId, type).stream()
                .map(task -> mapper.toDto(task,
                        fleetByType.getOrDefault(task.getAircraftType().getIcaoType(), 0)))
                .toList();
    }

    @Override
    public ProgrammeTaskDto findProgrammeTask(UUID tenantId, UUID programmeTaskId) {
        ProgrammeTask task = programmeTaskRepository.findOne(tenantId, programmeTaskId)
                .orElseThrow(() -> ResourceNotFoundException.of("Programme task", programmeTaskId));
        return mapper.toDto(task, 0);
    }

    @Override
    @Transactional
    public ProgrammeTaskDto saveProgrammeTask(UUID tenantId, SaveProgrammeTaskCommand command) {
        AircraftType type = aircraftTypeRepository.findByIcaoType(command.icaoType().trim().toUpperCase())
                .orElseThrow(() -> ResourceNotFoundException.of("Aircraft type", command.icaoType()));

        boolean mandatory = command.mandatory() == null || command.mandatory();
        boolean hasInterval = command.intervalHours() != null
                || command.intervalCycles() != null
                || command.intervalMonths() != null;
        if (mandatory && !hasInterval) {
            throw new BusinessRuleException("PROGRAMME_INTERVAL_REQUIRED",
                    "A mandatory task needs at least one limit: hours, cycles or months");
        }

        ProgrammeTask task = programmeTaskRepository.findProgramme(tenantId, type.getIcaoType()).stream()
                .filter(existing -> existing.getCode().equalsIgnoreCase(command.code()))
                .findFirst()
                .orElseGet(ProgrammeTask::new);

        task.setTenantId(tenantId);
        task.setAircraftType(type);
        task.setCode(command.code().trim().toUpperCase());
        task.setTitle(command.title().trim());
        task.setAtaChapter(command.ataChapter());
        task.setIntervalHours(command.intervalHours());
        task.setIntervalCycles(command.intervalCycles());
        task.setIntervalMonths(command.intervalMonths());
        task.setToleranceHours(command.toleranceHours());
        task.setToleranceDays(command.toleranceDays());
        task.setMandatory(mandatory);
        task.setReference(command.reference());
        return mapper.toDto(programmeTaskRepository.save(task), 0);
    }

    @Override
    public List<DirectiveDto> findDirectives(UUID tenantId, boolean outstandingOnly) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Directive> directives = directiveRepository.findAllForTenant(tenantId);
        if (directives.isEmpty()) {
            return List.of();
        }

        // Every application of every directive in one statement, then grouped in
        // memory — never one query per directive.
        Map<UUID, List<DirectiveApplicationDto>> byDirective = new HashMap<>();
        for (DirectiveApplication application : applicationRepository.findAllForTenant(tenantId)) {
            byDirective.computeIfAbsent(application.getDirective().getId(), key -> new ArrayList<>())
                    .add(mapper.toDto(application));
        }

        return directives.stream()
                .map(directive -> mapper.toDto(
                        directive, byDirective.getOrDefault(directive.getId(), List.of()), today))
                .filter(dto -> !outstandingOnly || dto.aircraftOutstanding() > 0)
                .toList();
    }

    @Override
    public DirectiveDto findDirective(UUID tenantId, UUID directiveId) {
        Directive directive = directiveRepository.findOne(tenantId, directiveId)
                .orElseThrow(() -> ResourceNotFoundException.of("Directive", directiveId));
        List<DirectiveApplicationDto> applications = applicationRepository
                .findByDirective(tenantId, directiveId).stream()
                .map(mapper::toDto)
                .toList();
        return mapper.toDto(directive, applications, LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Registering a directive and computing who it hits is one transaction: the
     * applicability is part of the fact, not a follow-up chore.
     */
    @Override
    @Transactional
    public DirectiveDto createDirective(UUID tenantId, SaveDirectiveCommand command) {
        directiveRepository.findByTenantIdAndReference(tenantId, command.reference().trim())
                .ifPresent(existing -> {
                    throw new BusinessRuleException("DIRECTIVE_EXISTS",
                            "Directive " + existing.getReference() + " is already registered");
                });

        Directive directive = new Directive();
        directive.setTenantId(tenantId);
        directive.setKind(parseKind(command.kind()));
        directive.setReference(command.reference().trim());
        directive.setSubject(command.subject().trim());
        directive.setIssuedBy(command.issuedBy());
        directive.setIssuedOn(command.issuedOn());
        directive.setEffectiveOn(command.effectiveOn());
        directive.setComplianceByDate(command.complianceByDate());
        directive.setComplianceByHours(command.complianceByHours());
        directive.setMethod(command.method());
        directive.setRecurringMonths(command.recurringMonths());

        AircraftType type = null;
        if (command.icaoType() != null && !command.icaoType().isBlank()) {
            type = aircraftTypeRepository.findByIcaoType(command.icaoType().trim().toUpperCase())
                    .orElseThrow(() -> ResourceNotFoundException.of("Aircraft type", command.icaoType()));
            directive.setAircraftType(type);
        }
        Directive saved = directiveRepository.save(directive);

        List<Aircraft> affected = aircraftRepository.findFleet(tenantId).stream()
                .filter(aircraft -> directive.getAircraftType() == null
                        || aircraft.getAircraftType().getId().equals(directive.getAircraftType().getId()))
                .toList();

        List<DirectiveApplication> applications = new ArrayList<>(affected.size());
        for (Aircraft aircraft : affected) {
            DirectiveApplication application = new DirectiveApplication();
            application.setTenantId(tenantId);
            application.setDirective(saved);
            application.setAircraft(aircraft);
            application.setStatus(DirectiveStatus.OPEN);
            applications.add(application);
        }
        applicationRepository.saveAll(applications);

        return mapper.toDto(saved,
                applications.stream().map(mapper::toDto).toList(),
                LocalDate.now(ZoneOffset.UTC));
    }

    @Override
    @Transactional
    public DirectiveApplicationDto comply(UUID tenantId, UUID applicationId, ComplyDirectiveCommand command) {
        DirectiveApplication application = applicationRepository.findByTenantIdAndId(tenantId, applicationId)
                .orElseThrow(() -> ResourceNotFoundException.of("Directive application", applicationId));
        DirectiveStatus target = parseStatus(command.status());

        if (target == DirectiveStatus.COMPLIED && command.compliedOn().isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new BusinessRuleException("DIRECTIVE_DATE_IN_FUTURE",
                    "A directive cannot be recorded as complied in the future");
        }
        if (target == DirectiveStatus.NOT_APPLICABLE
                && (command.remark() == null || command.remark().isBlank())) {
            // Declaring a mandatory instruction not applicable is a decision that
            // has to be justified in writing, and the record is where it lives.
            throw new BusinessRuleException("DIRECTIVE_JUSTIFICATION_REQUIRED",
                    "Declaring a directive not applicable requires a written justification");
        }

        application.setStatus(target);
        application.setCompliedOn(target == DirectiveStatus.COMPLIED ? command.compliedOn() : null);
        application.setCompliedRef(command.compliedRef());
        application.setRemark(command.remark());
        return mapper.toDto(applicationRepository.save(application));
    }

    @Override
    public List<DirectiveApplicationDto> findByAircraft(UUID tenantId, UUID aircraftId) {
        return applicationRepository.findByAircraft(tenantId, aircraftId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    public Map<UUID, Integer> countOutstandingDirectives(UUID tenantId) {
        return applicationRepository.countOutstandingByAircraft(tenantId).stream()
                .collect(Collectors.toMap(AircraftCount::aircraftId, AircraftCount::intValue));
    }

    private DirectiveKind parseKind(String value) {
        try {
            return DirectiveKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("DIRECTIVE_KIND_UNKNOWN", "Unknown directive kind: " + value);
        }
    }

    private DirectiveStatus parseStatus(String value) {
        try {
            return DirectiveStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("DIRECTIVE_STATUS_UNKNOWN", "Unknown directive status: " + value);
        }
    }
}
