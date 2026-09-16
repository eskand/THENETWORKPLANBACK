package com.thenetworkplan.networkplan.safety.mapper;

import com.thenetworkplan.networkplan.safety.domain.Campaign;
import com.thenetworkplan.networkplan.safety.domain.Occurrence;
import com.thenetworkplan.networkplan.safety.domain.SafetyAction;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.ActionDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.CampaignDto;
import com.thenetworkplan.networkplan.safety.dto.SafetyDtos.OccurrenceDto;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SafetyMapper {

    public ActionDto toDto(SafetyAction action, LocalDate today) {
        Long daysToDue = action.getDueOn() == null
                ? null
                : ChronoUnit.DAYS.between(today, action.getDueOn());
        boolean overdue = action.getStatus().isOutstanding()
                && daysToDue != null && daysToDue < 0;

        return new ActionDto(
                action.getId(),
                action.getOccurrence() == null ? null : action.getOccurrence().getId(),
                action.getOccurrence() == null ? null : action.getOccurrence().getReference(),
                action.getReference(),
                action.getTitle(),
                action.getDetail(),
                action.getOwnerUserId(),
                action.getDueOn(),
                daysToDue,
                action.getStatus().name(),
                action.getCompletedOn(),
                action.getEffectiveness(),
                overdue);
    }

    /**
     * The anonymity of a report is enforced here, once.
     *
     * <p>A screen cannot bypass it, because the name never leaves this method
     * when {@code anonymous} is set.
     */
    public OccurrenceDto toDto(Occurrence occurrence, List<ActionDto> actions) {
        int open = (int) actions.stream()
                .filter(action -> "OPEN".equals(action.status()) || "IN_PROGRESS".equals(action.status()))
                .count();

        return new OccurrenceDto(
                occurrence.getId(),
                occurrence.getReference(),
                occurrence.getOccurredAt(),
                occurrence.getReportedAt(),
                occurrence.isAnonymous() || occurrence.getReportedBy() == null
                        ? null
                        : occurrence.getReportedBy().fullName(),
                occurrence.isAnonymous(),
                occurrence.getLegId(),
                occurrence.getAircraft() == null ? null : occurrence.getAircraft().getRegistration(),
                occurrence.getStationIcao(),
                occurrence.getCategory().name(),
                occurrence.getTitle(),
                occurrence.getNarrative(),
                occurrence.getPhaseOfFlight(),
                occurrence.getEccairsEventType(),
                occurrence.getEccairsOccurrenceClass(),
                occurrence.getEccairsExportedAt(),
                occurrence.getEccairsReference(),
                occurrence.getRiskSeverity(),
                occurrence.getRiskProbability(),
                occurrence.getRiskLevel() == null ? null : occurrence.getRiskLevel().name(),
                occurrence.getRiskAssessedAt(),
                occurrence.getStatus().name(),
                occurrence.getClosedAt(),
                actions,
                open);
    }

    /**
     * @param audienceSize   people the campaign is addressed to
     * @param acknowledgements how many have acknowledged it
     */
    public CampaignDto toDto(Campaign campaign, int audienceSize, int acknowledgements) {
        Integer reach = null;
        if (campaign.isAcknowledgementRequired() && audienceSize > 0) {
            reach = (acknowledgements * 100) / audienceSize;
        }
        return new CampaignDto(
                campaign.getId(),
                campaign.getReference(),
                campaign.getKind(),
                kindLabel(campaign.getKind()),
                kindColour(campaign.getKind()),
                campaign.getAuthorName(),
                campaign.getPublishedOn(),
                campaign.getTitle(),
                campaign.getTheme(),
                campaign.getMessage(),
                campaign.getStartsOn(),
                campaign.getEndsOn(),
                campaign.getAudience(),
                campaign.getStatus(),
                campaign.isAcknowledgementRequired(),
                audienceSize,
                acknowledgements,
                reach);
    }

    /**
     * The wording each kind carries.
     *
     * <p>Here rather than in the browser because the same four words appear on
     * Safety Promotion, on the Safety Manager's promotion tab and on any export
     * — three places that would otherwise each grow their own translation.
     */
    private String kindLabel(String kind) {
        return switch (kind) {
            case "ALERT" -> "Safety alert";
            case "LESSON" -> "Lesson learned";
            case "POLICY" -> "Policy";
            default -> "Safety bulletin";
        };
    }

    /**
     * The colour each kind carries, from the approved prototype.
     *
     * <p>Sent with the row for the same reason as the label: the left border of
     * a communication and the pill on its corner have to agree, and they are
     * drawn by different components.
     */
    private String kindColour(String kind) {
        return switch (kind) {
            case "ALERT" -> "#C0392B";
            case "LESSON" -> "#E67E22";
            case "POLICY" -> "#7c3aed";
            default -> "#00b4d8";
        };
    }
}
