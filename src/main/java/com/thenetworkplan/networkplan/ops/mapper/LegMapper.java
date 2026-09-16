package com.thenetworkplan.networkplan.ops.mapper;

import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.ops.domain.Alert;
import com.thenetworkplan.networkplan.ops.domain.DelayRecord;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.domain.Release;
import com.thenetworkplan.networkplan.ops.dto.AlertDto;
import com.thenetworkplan.networkplan.ops.dto.DelayRecordDto;
import com.thenetworkplan.networkplan.ops.dto.LegDto;
import com.thenetworkplan.networkplan.ops.dto.ReleaseDto;
import org.springframework.stereotype.Component;

@Component
public class LegMapper {

    /**
     * Expects the leg to come from a query that fetch-joined the aircraft, its
     * type and the trip. Reading them here on a lazily loaded leg is what turns a
     * board render into an N+1.
     */
    public LegDto toDto(Leg leg) {
        Aircraft aircraft = leg.getAircraft();
        return new LegDto(
                leg.getId(),
                leg.getTrip() == null ? null : leg.getTrip().getId(),
                leg.getTrip() == null ? null : leg.getTrip().getClientRef(),
                leg.getFlightNo(),
                aircraft.getId(),
                aircraft.getRegistration(),
                aircraft.getAircraftType().getIcaoType(),
                aircraft.getAircraftType().getModel(),
                aircraft.getStatus().name(),
                leg.getDepIcao(),
                leg.getArrIcao(),
                leg.getBaseIcao(),
                leg.getStd(),
                leg.getSta(),
                leg.getEtd(),
                leg.getEta(),
                leg.getOutAt(),
                leg.getOffAt(),
                leg.getOnAt(),
                leg.getInAt(),
                leg.getCtot(),
                leg.getStatus().name(),
                leg.getFlightType().name(),
                leg.getPaxCount(),
                leg.getRiskLevel() == null ? null : leg.getRiskLevel().name(),
                leg.getMvtSentAt(),
                leg.getRemark(),
                leg.getBusinessKey());
    }

    public ReleaseDto toDto(Release release) {
        return new ReleaseDto(
                release.getId(),
                release.getLegId(),
                release.getVersion(),
                release.getSignedBy(),
                release.getSignedAt(),
                release.isDerogation(),
                release.getDerogationReason(),
                release.getCaptainAckBy(),
                release.getCaptainAckAt(),
                release.acknowledged());
    }

    public AlertDto toDto(Alert alert) {
        return new AlertDto(
                alert.getId(),
                alert.getLegId(),
                alert.getSeverity().name(),
                alert.getRule(),
                alert.getCause(),
                alert.getTargetRole(),
                alert.getCreatedAt());
    }

    public DelayRecordDto toDto(DelayRecord record) {
        return new DelayRecordDto(
                record.getId(),
                record.getLegId(),
                record.getMinutes(),
                record.getCode(),
                record.getSubCode(),
                record.getRemark());
    }
}
