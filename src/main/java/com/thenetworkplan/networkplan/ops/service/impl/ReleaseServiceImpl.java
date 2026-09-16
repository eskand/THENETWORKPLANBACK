package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.common.domain.Source;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.common.util.Json;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.domain.Release;
import com.thenetworkplan.networkplan.ops.dto.AcknowledgeReleaseCommand;
import com.thenetworkplan.networkplan.ops.dto.ReadinessDto;
import com.thenetworkplan.networkplan.ops.dto.ReadinessItem;
import com.thenetworkplan.networkplan.ops.dto.ReleaseDto;
import com.thenetworkplan.networkplan.ops.dto.SignReleaseCommand;
import com.thenetworkplan.networkplan.ops.mapper.LegMapper;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.repository.ReleaseRepository;
import com.thenetworkplan.networkplan.ops.service.LegEventRecorder;
import com.thenetworkplan.networkplan.ops.service.LegReadinessService;
import com.thenetworkplan.networkplan.ops.service.ReleaseService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ReleaseServiceImpl implements ReleaseService {

    private final ReleaseRepository releaseRepository;
    private final LegRepository legRepository;
    private final LegReadinessService readinessService;
    private final LegEventRecorder eventRecorder;
    private final LegMapper mapper;

    public ReleaseServiceImpl(ReleaseRepository releaseRepository,
                              LegRepository legRepository,
                              LegReadinessService readinessService,
                              LegEventRecorder eventRecorder,
                              LegMapper mapper) {
        this.releaseRepository = releaseRepository;
        this.legRepository = legRepository;
        this.readinessService = readinessService;
        this.eventRecorder = eventRecorder;
        this.mapper = mapper;
    }

    @Override
    public Optional<ReleaseDto> findCurrent(UUID tenantId, UUID legId) {
        return releaseRepository.findFirstByTenantIdAndLegIdOrderByVersionDesc(tenantId, legId)
                .map(mapper::toDto);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public ReleaseDto sign(UUID tenantId, UUID legId, SignReleaseCommand command) {
        Leg leg = legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));
        if (!leg.getStatus().isOpen()) {
            throw new BusinessRuleException("LEG_NOT_OPEN",
                    "A " + leg.getStatus() + " leg cannot be released");
        }

        ReadinessDto readiness = readinessService.assess(tenantId, legId);

        // A blocking finding is not a warning to click through.
        if (!readiness.blocking().isEmpty()) {
            throw new BusinessRuleException("RELEASE_BLOCKED",
                    "Release refused, " + readiness.blocking().size()
                            + " blocking finding(s): " + describe(readiness));
        }
        if (!readiness.derogable().isEmpty() && !command.derogation()) {
            throw new BusinessRuleException("RELEASE_NEEDS_DEROGATION",
                    "Release requires an explicit derogation: " + describe(readiness));
        }
        if (command.derogation()
                && (command.derogationReason() == null || command.derogationReason().isBlank())) {
            throw new BusinessRuleException("DEROGATION_REASON_REQUIRED",
                    "A derogation must carry a reason");
        }

        int nextVersion = releaseRepository
                .findFirstByTenantIdAndLegIdOrderByVersionDesc(tenantId, legId)
                .map(existing -> existing.getVersion() + 1)
                .orElse(1);

        Release release = new Release();
        release.setTenantId(tenantId);
        release.setLegId(legId);
        release.setVersion(nextVersion);
        release.setSignedBy(command.signedBy());
        release.setSignedAt(OffsetDateTime.now());
        release.setDerogation(command.derogation());
        release.setDerogationReason(command.derogationReason());
        release.setBlockingChecks(freeze(readiness));
        release.setSource(Source.manual(command.signedBy(), "dispatch release"));
        Release saved = releaseRepository.save(release);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("releaseVersion", nextVersion);
        after.put("derogation", command.derogation());
        leg.setStatus(LegStatus.RELEASED);
        legRepository.save(leg);

        eventRecorder.record(tenantId, legId, LegEventKind.RELEASED, null, after,
                command.signedBy(), command.derogationReason());
        return mapper.toDto(saved);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public ReleaseDto acknowledge(UUID tenantId, UUID legId, AcknowledgeReleaseCommand command) {
        Release release = releaseRepository
                .findFirstByTenantIdAndLegIdOrderByVersionDesc(tenantId, legId)
                .orElseThrow(() -> new BusinessRuleException("RELEASE_MISSING",
                        "There is nothing to acknowledge: this leg has no release"));
        if (release.getSignedAt() == null) {
            throw new BusinessRuleException("RELEASE_NOT_SIGNED",
                    "The dispatcher has not signed this release yet");
        }
        release.setCaptainAckBy(command.acknowledgedBy());
        release.setCaptainAckAt(OffsetDateTime.now());
        return mapper.toDto(releaseRepository.save(release));
    }

    /** Freezes the findings as they stood at signature, for later audit. */
    private String freeze(ReadinessDto readiness) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("assessedAt", OffsetDateTime.now());
        values.put("blocking", join(readiness.blocking()));
        values.put("derogable", join(readiness.derogable()));
        values.put("info", join(readiness.info()));
        return Json.object(values);
    }

    private String join(List<ReadinessItem> items) {
        return items.stream()
                .map(item -> item.check() + ": " + item.message())
                .collect(Collectors.joining(" | "));
    }

    private String describe(ReadinessDto readiness) {
        String blocking = join(readiness.blocking());
        return blocking.isBlank() ? join(readiness.derogable()) : blocking;
    }
}
