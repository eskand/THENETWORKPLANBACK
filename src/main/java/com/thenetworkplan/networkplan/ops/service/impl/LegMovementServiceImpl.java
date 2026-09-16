package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.config.cache.CacheNames;
import com.thenetworkplan.networkplan.ops.domain.DelayRecord;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import com.thenetworkplan.networkplan.ops.domain.LegStatus;
import com.thenetworkplan.networkplan.ops.domain.MovementKind;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.dto.RecordMovementCommand;
import com.thenetworkplan.networkplan.ops.mapper.LegMapper;
import com.thenetworkplan.networkplan.ops.repository.DelayCodeRepository;
import com.thenetworkplan.networkplan.ops.repository.DelayRecordRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.LegEventRecorder;
import com.thenetworkplan.networkplan.ops.service.LegMovementService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LegMovementServiceImpl implements LegMovementService {

    private final LegRepository legRepository;
    private final DelayRecordRepository delayRecordRepository;
    private final DelayCodeRepository delayCodeRepository;
    private final LegEventRecorder eventRecorder;
    private final LegMapper mapper;

    public LegMovementServiceImpl(LegRepository legRepository,
                                  DelayRecordRepository delayRecordRepository,
                                  DelayCodeRepository delayCodeRepository,
                                  LegEventRecorder eventRecorder,
                                  LegMapper mapper) {
        this.legRepository = legRepository;
        this.delayRecordRepository = delayRecordRepository;
        this.delayCodeRepository = delayCodeRepository;
        this.eventRecorder = eventRecorder;
        this.mapper = mapper;
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto recordMovement(UUID tenantId, UUID legId, RecordMovementCommand command, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getStatus() == LegStatus.CANCELLED) {
            throw new BusinessRuleException("LEG_CANCELLED", "A cancelled leg has no movements");
        }
        if (leg.getStatus() == LegStatus.CLOSED) {
            throw new BusinessRuleException("LEG_CLOSED", "A closed leg cannot receive a movement");
        }

        MovementKind kind = parseKind(command.kind());
        Map<String, Object> before = snapshot(leg);

        switch (kind) {
            case OUT -> {
                leg.setOutAt(command.at());
                leg.setStatus(LegStatus.DEPARTED);
            }
            case OFF -> {
                requirePrevious(leg.getOutAt(), "OUT", "OFF");
                requireChronology(leg.getOutAt(), command.at(), "OFF cannot precede OUT");
                leg.setOffAt(command.at());
            }
            case ON -> {
                requirePrevious(leg.getOffAt(), "OFF", "ON");
                requireChronology(leg.getOffAt(), command.at(), "ON cannot precede OFF");
                leg.setOnAt(command.at());
                leg.setStatus(LegStatus.ARRIVED);
            }
            case IN -> {
                requirePrevious(leg.getOnAt(), "ON", "IN");
                requireChronology(leg.getOnAt(), command.at(), "IN cannot precede ON");
                leg.setInAt(command.at());
            }
        }

        Leg saved = legRepository.save(leg);
        eventRecorder.record(tenantId, legId, LegEventKind.MOVEMENT, before, snapshot(saved),
                actorId, kind + " recorded");

        if (command.delayMinutes() != null) {
            String code = command.delayCode() == null || command.delayCode().isBlank()
                    ? "89" : command.delayCode().trim();
            delayCodeRepository.findByTenantIdAndCode(tenantId, code)
                    .orElseThrow(() -> new BusinessRuleException("DELAY_CODE_UNKNOWN",
                            "Delay code " + code + " is not in the tenant list"));
            DelayRecord record = new DelayRecord();
            record.setTenantId(tenantId);
            record.setLegId(legId);
            record.setMinutes(command.delayMinutes());
            record.setCode(code);
            record.setRemark(command.remark());
            delayRecordRepository.save(record);
        }
        return mapper.toDto(saved);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto markMvtSent(UUID tenantId, UUID legId, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getOutAt() == null) {
            throw new BusinessRuleException("MVT_TOO_EARLY",
                    "A movement message needs at least an off-block time");
        }
        Map<String, Object> before = snapshot(leg);
        leg.setMvtSentAt(OffsetDateTime.now());
        Leg saved = legRepository.save(leg);
        eventRecorder.record(tenantId, legId, LegEventKind.MOVEMENT, before, snapshot(saved),
                actorId, "MVT sent");
        return mapper.toDto(saved);
    }

    @Override
    @CacheEvict(cacheNames = CacheNames.DISPATCH_BOARD, allEntries = true)
    public LegDto close(UUID tenantId, UUID legId, UUID actorId) {
        Leg leg = requireLeg(tenantId, legId);
        if (leg.getInAt() == null) {
            throw new BusinessRuleException("LEG_NOT_ON_BLOCKS",
                    "A leg cannot be closed before its on-block time is recorded");
        }
        Map<String, Object> before = snapshot(leg);
        leg.setStatus(LegStatus.CLOSED);
        Leg saved = legRepository.save(leg);
        eventRecorder.record(tenantId, legId, LegEventKind.CLOSED, before, snapshot(saved),
                actorId, "Flight closed");
        return mapper.toDto(saved);
    }

    private MovementKind parseKind(String raw) {
        try {
            return MovementKind.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("MOVEMENT_KIND_UNKNOWN",
                    "Movement must be one of OUT, OFF, ON, IN — received " + raw);
        }
    }

    private void requirePrevious(OffsetDateTime previous, String previousName, String kind) {
        if (previous == null) {
            throw new BusinessRuleException("MOVEMENT_OUT_OF_ORDER",
                    kind + " cannot be recorded before " + previousName);
        }
    }

    private void requireChronology(OffsetDateTime previous, OffsetDateTime candidate, String message) {
        if (candidate.isBefore(previous)) {
            throw new BusinessRuleException("MOVEMENT_OUT_OF_ORDER", message);
        }
    }

    private Leg requireLeg(UUID tenantId, UUID legId) {
        return legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));
    }

    private Map<String, Object> snapshot(Leg leg) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", leg.getStatus());
        values.put("outAt", leg.getOutAt());
        values.put("offAt", leg.getOffAt());
        values.put("onAt", leg.getOnAt());
        values.put("inAt", leg.getInAt());
        values.put("mvtSentAt", leg.getMvtSentAt());
        return values;
    }
}
