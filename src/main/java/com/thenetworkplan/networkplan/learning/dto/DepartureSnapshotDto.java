package com.thenetworkplan.networkplan.learning.dto;

import com.thenetworkplan.networkplan.learning.domain.DepartureSnapshot;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une photo a H-1, telle que l'export la rend au script d'entrainement.
 * Les noms suivent les colonnes de {@code ops.departure_snapshots} en camelCase.
 */
public record DepartureSnapshotDto(
        UUID id,
        UUID legId,
        OffsetDateTime takenAt,
        int leadMinutes,
        String flightNo,
        String registration,
        String icaoType,
        String depIcao,
        String arrIcao,
        OffsetDateTime std,
        OffsetDateTime sta,
        Integer legIndex,
        Integer schedTurnaroundMin,
        boolean inboundKnown,
        Integer inboundDelayMin,
        int depCongestion,
        int arrCongestion,
        Integer permitsTotal,
        Integer permitsOutstanding,
        Integer servicesTotal,
        Integer servicesConfirmed,
        String servicesReadiness,
        Boolean crewComplete,
        String crewFtlStatus,
        String crewDocumentStatus,
        int melOpen,
        boolean melBlocking,
        String riskLevel,
        Integer riskIndex,
        OffsetDateTime depWxObservedAt,
        Integer depWxAgeMin,
        Integer depWindDirDeg,
        Integer depWindKt,
        Integer depWindGustKt,
        Integer depVisibilityM,
        Integer depCeilingFt,
        Boolean depCavok,
        String depConditions,
        String depFlightCategory,
        OffsetDateTime arrWxObservedAt,
        Integer arrWxAgeMin,
        Integer arrWindDirDeg,
        Integer arrWindKt,
        Integer arrWindGustKt,
        Integer arrVisibilityM,
        Integer arrCeilingFt,
        Boolean arrCavok,
        String arrConditions,
        String arrFlightCategory,
        OffsetDateTime outAt,
        Integer depDelayMin,
        Boolean targetDelay15,
        String delayCode,
        boolean cancelled,
        OffsetDateTime outcomeAt) implements Serializable {

    public static DepartureSnapshotDto of(DepartureSnapshot s) {
        return new DepartureSnapshotDto(
                s.getId(), s.getLegId(), s.getTakenAt(), s.getLeadMinutes(),
                s.getFlightNo(), s.getRegistration(), s.getIcaoType(), s.getDepIcao(), s.getArrIcao(),
                s.getStd(), s.getSta(),
                s.getLegIndex(), s.getSchedTurnaroundMin(), s.isInboundKnown(), s.getInboundDelayMin(),
                s.getDepCongestion(), s.getArrCongestion(),
                s.getPermitsTotal(), s.getPermitsOutstanding(),
                s.getServicesTotal(), s.getServicesConfirmed(), s.getServicesReadiness(),
                s.getCrewComplete(), s.getCrewFtlStatus(), s.getCrewDocumentStatus(),
                s.getMelOpen(), s.isMelBlocking(), s.getRiskLevel(), s.getRiskIndex(),
                s.getDepWxObservedAt(), s.getDepWxAgeMin(), s.getDepWindDirDeg(), s.getDepWindKt(),
                s.getDepWindGustKt(), s.getDepVisibilityM(), s.getDepCeilingFt(), s.getDepCavok(),
                s.getDepConditions(), s.getDepFlightCategory(),
                s.getArrWxObservedAt(), s.getArrWxAgeMin(), s.getArrWindDirDeg(), s.getArrWindKt(),
                s.getArrWindGustKt(), s.getArrVisibilityM(), s.getArrCeilingFt(), s.getArrCavok(),
                s.getArrConditions(), s.getArrFlightCategory(),
                s.getOutAt(), s.getDepDelayMin(), s.getTargetDelay15(), s.getDelayCode(),
                s.isCancelled(), s.getOutcomeAt());
    }
}
