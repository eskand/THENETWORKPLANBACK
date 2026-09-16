package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.ops.domain.Alert;
import com.thenetworkplan.networkplan.ops.dto.AlertDto;
import com.thenetworkplan.networkplan.ops.mapper.LegMapper;
import com.thenetworkplan.networkplan.ops.repository.AlertRepository;
import com.thenetworkplan.networkplan.ops.service.AlertService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final LegMapper mapper;

    public AlertServiceImpl(AlertRepository alertRepository, LegMapper mapper) {
        this.alertRepository = alertRepository;
        this.mapper = mapper;
    }

    @Override
    public List<AlertDto> findOpen(UUID tenantId) {
        return alertRepository.findOpen(tenantId).stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public AlertDto acknowledge(UUID tenantId, UUID alertId, UUID actorId) {
        Alert alert = alertRepository.findByTenantIdAndId(tenantId, alertId)
                .orElseThrow(() -> ResourceNotFoundException.of("Alert", alertId));
        if (alert.open()) {
            alert.setAckedBy(actorId);
            alert.setAckedAt(OffsetDateTime.now());
            alert = alertRepository.save(alert);
        }
        return mapper.toDto(alert);
    }
}
