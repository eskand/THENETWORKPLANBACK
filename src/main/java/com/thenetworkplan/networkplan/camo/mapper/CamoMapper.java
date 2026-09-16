package com.thenetworkplan.networkplan.camo.mapper;

import com.thenetworkplan.networkplan.camo.domain.AircraftTask;
import com.thenetworkplan.networkplan.camo.domain.AirworthinessReview;
import com.thenetworkplan.networkplan.camo.domain.ArcVerdict;
import com.thenetworkplan.networkplan.camo.domain.LifeLimitedPart;
import com.thenetworkplan.networkplan.camo.domain.Utilisation;
import com.thenetworkplan.networkplan.camo.domain.WorkOrder;
import com.thenetworkplan.networkplan.camo.dto.ArcDto;
import com.thenetworkplan.networkplan.camo.dto.DueItemDto;
import com.thenetworkplan.networkplan.camo.dto.LifeLimitedPartDto;
import com.thenetworkplan.networkplan.camo.dto.UtilisationRowDto;
import com.thenetworkplan.networkplan.camo.dto.WorkOrderDto;
import com.thenetworkplan.networkplan.camo.service.MaintenanceDueRule;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class CamoMapper {

    /** Under this share of life left, the part is what grounds the aircraft next. */
    private static final int CRITICAL_PERCENT = 15;

    /** Under this share, it belongs on the procurement plan rather than in a gauge. */
    private static final int WATCH_PERCENT = 35;

    /** @param verdict from {@code MaintenanceDueRule}, computed once by the service */
    public DueItemDto toDto(AircraftTask task, MaintenanceDueRule.Verdict verdict) {
        return new DueItemDto(
                task.getId(),
                task.getAircraft().getId(),
                task.getAircraft().getRegistration(),
                task.getAircraft().getAircraftType() == null
                        ? null
                        : task.getAircraft().getAircraftType().getIcaoType(),
                task.getCode(),
                task.getTitle(),
                task.getLastDoneOn(),
                task.getDueOn(),
                task.getDueAtHours(),
                task.getDueAtCycles(),
                verdict.remainingHours(),
                verdict.remainingDays(),
                verdict.remainingCycles(),
                verdict.drivingLimit(),
                verdict.status().name());
    }

    public UtilisationRowDto toDto(Utilisation utilisation) {
        return new UtilisationRowDto(
                utilisation.getId(),
                utilisation.getLegId(),
                utilisation.getFlownOn(),
                utilisation.getBlockMinutes(),
                utilisation.getAirMinutes(),
                utilisation.getCycles(),
                utilisation.getSource() == null ? null : utilisation.getSource().getType(),
                utilisation.getSource() == null ? null : utilisation.getSource().getReference());
    }

    /**
     * @param on the day the certificate is read against — passed in rather than
     *           taken from the clock here, so every row of one response is
     *           measured against the same day even across midnight
     */
    public ArcDto toDto(AirworthinessReview review, LocalDate on) {
        long days = review.daysLeft(on);
        return new ArcDto(
                review.getId(),
                review.getAircraft().getId(),
                review.getAircraft().getRegistration(),
                review.getCertificateNo(),
                review.getIssuedOn(),
                review.getExpiresOn(),
                days,
                ArcVerdict.of(days).name(),
                review.getReviewBasis().name(),
                // The wording as the reviewer signed it: "Full review — M.A.710(a)".
                // The enum says which of three grounds; this says under which rule.
                review.getRemark(),
                review.getReviewedBy(),
                review.getReviewerApprovalNo(),
                review.getCofaRef(),
                review.isInForce());
    }

    public LifeLimitedPartDto toDto(LifeLimitedPart part, LocalDate on) {
        int percent = (int) Math.round(part.fractionRemaining(on) * 100);
        return new LifeLimitedPartDto(
                part.getId(),
                part.getAircraft().getId(),
                part.getAircraft().getRegistration(),
                part.getAircraft().getAircraftType() == null
                        ? null
                        : part.getAircraft().getAircraftType().getIcaoType(),
                part.getName(),
                part.getPartNo(),
                part.getSerialNo(),
                part.getPosition(),
                part.getLimitCycles(),
                part.getUsedCycles(),
                part.cyclesRemaining(),
                part.getLimitMonths(),
                part.getInstalledOn(),
                percent,
                governingLimit(part, on),
                percent < CRITICAL_PERCENT ? "CRITICAL" : percent < WATCH_PERCENT ? "WATCH" : "OK");
    }

    public WorkOrderDto toDto(WorkOrder order, LocalDate on) {
        return new WorkOrderDto(
                order.getId(),
                order.getAircraft().getId(),
                order.getAircraft().getRegistration(),
                order.getOrderNo(),
                order.getTitle(),
                order.getStatus().name(),
                order.getFacility(),
                order.getFacilityIcao(),
                order.getOpenedOn(),
                order.getTargetOn(),
                order.getTargetNote(),
                order.getClosedOn(),
                order.getLabourHours(),
                order.isOverdue(on));
    }

    /**
     * Which of the three possible limits is nearest.
     *
     * <p>Named so that a part at eight per cent says whether flying it less
     * would help: a calendar limit runs down whether the aircraft moves or not.
     */
    private String governingLimit(LifeLimitedPart part, LocalDate on) {
        String governing = null;
        double worst = Double.MAX_VALUE;
        if (part.getLimitCycles() != null && part.getLimitCycles() > 0) {
            worst = 1.0 - (double) part.getUsedCycles() / part.getLimitCycles();
            governing = "CYCLES";
        }
        if (part.getLimitHours() != null && part.getLimitHours().signum() > 0
                && part.getUsedHours() != null) {
            double left = 1.0 - part.getUsedHours().doubleValue() / part.getLimitHours().doubleValue();
            if (left < worst) {
                worst = left;
                governing = "HOURS";
            }
        }
        if (part.getLimitMonths() != null && part.getLimitMonths() > 0 && part.getInstalledOn() != null) {
            double used = java.time.temporal.ChronoUnit.MONTHS
                    .between(part.getInstalledOn(), on);
            if (1.0 - used / part.getLimitMonths() < worst) {
                governing = "MONTHS";
            }
        }
        return governing;
    }
}
