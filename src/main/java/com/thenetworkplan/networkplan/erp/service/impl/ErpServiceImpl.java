package com.thenetworkplan.networkplan.erp.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.erp.domain.ActivationKind;
import com.thenetworkplan.networkplan.erp.domain.ErpActivation;
import com.thenetworkplan.networkplan.common.tenant.TenantContext;
import com.thenetworkplan.networkplan.erp.domain.ErpExercise;
import com.thenetworkplan.networkplan.erp.domain.ErpLevel;
import com.thenetworkplan.networkplan.erp.domain.ErpPlan;
import com.thenetworkplan.networkplan.erp.dto.ErpCommands.ActivateCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpCommands.StandDownCommand;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpActivationDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpBoardDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpExerciseDto;
import com.thenetworkplan.networkplan.erp.dto.ErpDtos.ErpRoleDto;
import com.thenetworkplan.networkplan.erp.repository.ErpActivationRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpExerciseRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpLevelRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpPlanRepository;
import com.thenetworkplan.networkplan.erp.repository.ErpRoleRepository;
import com.thenetworkplan.networkplan.erp.service.ErpService;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ERP.
 *
 * <p>Three things the screen must be able to say, and each is a stored fact:
 * whether the plan's review is overdue, which roles have no deputy, and how
 * often the plan has actually been exercised. A plan that is never exercised
 * and never reviewed looks identical to a good one on paper — not here.
 */
@Service
@Transactional(readOnly = true)
public class ErpServiceImpl implements ErpService {

    private static final DateTimeFormatter REF_YEAR = DateTimeFormatter.ofPattern("yyyy");

    private final ErpPlanRepository planRepository;
    private final ErpRoleRepository roleRepository;
    private final ErpActivationRepository activationRepository;
    private final ErpExerciseRepository exerciseRepository;
    private final ErpLevelRepository levelRepository;

    public ErpServiceImpl(ErpPlanRepository planRepository,
                          ErpRoleRepository roleRepository,
                          ErpActivationRepository activationRepository,
                          ErpExerciseRepository exerciseRepository,
                          ErpLevelRepository levelRepository) {
        this.planRepository = planRepository;
        this.roleRepository = roleRepository;
        this.activationRepository = activationRepository;
        this.exerciseRepository = exerciseRepository;
        this.levelRepository = levelRepository;
    }

    @Override
    public ErpBoardDto findBoard(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        LocalDate today = now.toLocalDate();

        ErpPlan plan = planRepository.findByTenantIdOrderByApprovedOnDesc(tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No emergency response plan is recorded for this operator"));

        List<ErpRoleDto> roles = roleRepository.findByPlan(tenantId, plan.getId()).stream()
                .map(role -> new ErpRoleDto(
                        role.getId(), role.getRoleCode(), role.getRoleTitle(),
                        role.getScope(), role.getColour(),
                        role.getHolderUserId(), role.getDeputyUserId(), role.getPhone(),
                        role.getResponsibilities(), role.getCallOrder(),
                        role.getDeputyUserId() == null))
                .toList();

        List<ErpActivation> activations = activationRepository.findAllForTenant(tenantId).stream()
                .filter(activation -> activation.getPlan().getId().equals(plan.getId()))
                .toList();

        List<ErpActivationDto> activationDtos = activations.stream()
                .map(this::toActivationDto)
                .toList();

        OffsetDateTime twelveMonthsAgo = now.minusMonths(12);
        int exercises = (int) activations.stream()
                .filter(activation -> activation.getKind().isDrill())
                .filter(activation -> activation.getActivatedAt().isAfter(twelveMonthsAgo))
                .count();
        OffsetDateTime lastExercise = activations.stream()
                .filter(activation -> activation.getKind().isDrill())
                .map(ErpActivation::getActivatedAt)
                .max(OffsetDateTime::compareTo)
                .orElse(null);

        Long daysToReview = plan.getReviewDueOn() == null
                ? null
                : ChronoUnit.DAYS.between(today, plan.getReviewDueOn());

        return new ErpBoardDto(
                plan.getId(), plan.getCode(), plan.getTitle(), plan.getRevision(),
                plan.getApprovedOn(), plan.getReviewDueOn(), daysToReview,
                daysToReview != null && daysToReview < 0,
                plan.getSummary(), roles, activationDtos,
                activations.stream().filter(ErpActivation::isOpen).findFirst()
                        .map(this::toActivationDto).orElse(null),
                exerciseRepository.findByTenantIdOrderByHeldOnDesc(tenantId).stream()
                        .map(this::toExerciseDto).toList(),
                (int) roles.stream().filter(ErpRoleDto::singlePointOfFailure).count(),
                exercises, lastExercise,
                activations.stream().anyMatch(ErpActivation::isOpen),
                now);
    }

    @Override
    @Transactional
    public ErpActivationDto activate(UUID tenantId, ActivateCommand command, UUID actorId) {
        ErpPlan plan = planRepository.findByTenantIdOrderByApprovedOnDesc(tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No emergency response plan is recorded for this operator"));

        boolean alreadyOpen = activationRepository.findAllForTenant(tenantId).stream()
                .anyMatch(ErpActivation::isOpen);
        if (alreadyOpen) {
            // Two open activations would split the crisis organisation in two.
            throw new BusinessRuleException("ERP_ALREADY_ACTIVE",
                    "An activation is already open; stand it down before opening another");
        }

        ActivationKind kind = parseKind(command.kind());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ErpActivation activation = new ErpActivation();
        activation.setTenantId(tenantId);
        activation.setPlan(plan);
        activation.setKind(kind);
        activation.setReference(nextReference(tenantId, kind, now));
        activation.setLegId(command.legId());
        activation.setActivatedAt(now);
        activation.setActivatedBy(actorId);
        activation.setSituation(command.situation().trim());
        activation.setLevel(command.level());
        activation.setEventLabel(command.eventLabel().trim());
        activation.setInitiatedByName(command.initiatedByName().trim());
        activation.setInitiatedByRole(trim(command.initiatedByRole()));
        activation.setConcurredByName(trim(command.concurredByName()));
        activation.setConcurredByRole(trim(command.concurredByRole()));
        activation.setOverrideReason(trim(command.overrideReason()));
        activation.setEventCode(trim(command.eventCode()));
        activation.setFlight(trim(command.flight()));
        activation.setRegistration(trim(command.registration()));
        activation.setAircraftType(trim(command.aircraftType()));
        activation.setOrigin(trim(command.origin()));
        activation.setDestination(trim(command.destination()));
        activation.setPob(trim(command.pob()));

        /* La regle du plan : deux personnes, ou une derogation motivee. Elle
           est aussi dans la base ; ici pour donner un message utile plutot
           qu'une violation de contrainte. */
        if (kind == ActivationKind.REAL
                && activation.getConcurredByName() == null
                && activation.getOverrideReason() == null) {
            throw new BusinessRuleException("ERP_CONCURRENCE_REQUIRED",
                    "A real activation requires the OCC Manager and the Safety Manager acting "
                            + "together, or an Accountable Manager override with its reason");
        }

        return toActivationDto(activationRepository.save(activation));
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Override
    @Transactional
    public ErpActivationDto standDown(UUID tenantId, UUID activationId, StandDownCommand command) {
        ErpActivation activation = activationRepository.findByTenantIdAndId(tenantId, activationId)
                .orElseThrow(() -> ResourceNotFoundException.of("ERP activation", activationId));
        if (!activation.isOpen()) {
            throw new BusinessRuleException("ERP_ALREADY_STOOD_DOWN",
                    "This activation was stood down on " + activation.getStoodDownAt());
        }
        activation.setStoodDownAt(OffsetDateTime.now(ZoneOffset.UTC));
        activation.setStoodDownBy(trim(command.stoodDownBy()));
        activation.setSituation(activation.getSituation() + "\n\nStand down: " + command.outcome());
        return toActivationDto(activationRepository.save(activation));
    }

    @Override
    public ErpExerciseDto toExerciseDto(ErpExercise exercise) {
        return new ErpExerciseDto(exercise.getId(), exercise.getReference(),
                exercise.getExerciseType(), exercise.getScenario(), exercise.getHeldOn(),
                exercise.getLevel(), exercise.getParticipants(), exercise.getFindings(),
                exercise.getStatus(), exercise.getLessons());
    }

    @Override
    public ErpActivationDto toActivationDto(ErpActivation activation) {
        Long duration = activation.getStoodDownAt() == null
                ? null
                : Duration.between(activation.getActivatedAt(), activation.getStoodDownAt()).toMinutes();
        return new ErpActivationDto(
                activation.getId(), activation.getReference(), activation.getKind().name(),
                activation.getLevel(), levelName(activation.getLevel()),
                activation.getEventLabel(),
                activation.getInitiatedByName(), activation.getInitiatedByRole(),
                activation.getConcurredByName(), activation.getConcurredByRole(),
                activation.getOverrideReason(), activation.getStoodDownBy(),
                activation.getLegId(), activation.getActivatedAt(), activation.getStoodDownAt(),
                duration, activation.isOpen(), activation.getSituation());
    }

    /**
     * What each level means, in the operator's own words.
     *
     * <p>Read from {@code safety.erp_levels} rather than named here: the plan
     * says what a level 3 is called, and a copy of that scale in the code would
     * eventually disagree with the manual the authority was shown.
     */
    private String levelName(short level) {
        return levelRepository.findByTenantIdOrderByLevel(TenantContext.require()).stream()
                .filter(candidate -> candidate.getLevel() == level)
                .findFirst()
                .map(ErpLevel::getName)
                .orElse("Level " + level);
    }

    private String nextReference(UUID tenantId, ActivationKind kind, OffsetDateTime now) {
        String base = "ERP-" + switch (kind) {
            case EXERCISE -> "EX";
            case STANDBY -> "SB";
            case REAL -> "RE";
        } + "-" + REF_YEAR.format(now) + "-";
        for (int suffix = 1; suffix < 100; suffix++) {
            String candidate = base + String.format("%02d", suffix);
            if (activationRepository.findByTenantIdAndReference(tenantId, candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new BusinessRuleException("ERP_REFERENCE_EXHAUSTED",
                "More than ninety-nine activations of this kind this year");
    }

    private ActivationKind parseKind(String value) {
        try {
            return ActivationKind.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("ERP_KIND_UNKNOWN", "Unknown activation kind: " + value);
        }
    }
}
