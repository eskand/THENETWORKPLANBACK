package com.thenetworkplan.networkplan.ops.service.impl;

import com.thenetworkplan.networkplan.common.domain.Source;
import com.thenetworkplan.networkplan.common.util.Json;
import com.thenetworkplan.networkplan.ops.domain.LegEvent;
import com.thenetworkplan.networkplan.ops.domain.LegEventKind;
import com.thenetworkplan.networkplan.ops.repository.LegEventRepository;
import com.thenetworkplan.networkplan.ops.service.LegEventRecorder;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegEventRecorderImpl implements LegEventRecorder {

    private final LegEventRepository legEventRepository;

    public LegEventRecorderImpl(LegEventRepository legEventRepository) {
        this.legEventRepository = legEventRepository;
    }

    /**
     * Joins the caller's transaction rather than starting one: an event is only
     * true if the change it describes was committed.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID tenantId,
                       UUID legId,
                       LegEventKind kind,
                       Map<String, Object> before,
                       Map<String, Object> after,
                       UUID actorId,
                       String reason) {
        LegEvent event = new LegEvent();
        event.setTenantId(tenantId);
        event.setLegId(legId);
        event.setKind(kind);
        event.setPayloadBefore(Json.object(before));
        event.setPayloadAfter(Json.object(after));
        event.setActorId(actorId);
        event.setReason(reason);
        event.setSource(Source.manual(actorId, "leg command"));
        legEventRepository.save(event);
    }
}
