package com.thenetworkplan.networkplan.vigil.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Ce que le panneau VIGIL lit et ecrit. */
public final class VigilDtos {

    private VigilDtos() {
    }

    /**
     * Une alerte, telle que {@code alertHtml()} de l'annexe la dessine
     * (l. 99312) : nom, vol, gravite, pourquoi, action, identite, methode,
     * risque, statut, heure.
     */
    public record VigilAlertDto(
            UUID id,
            String signature,
            String rule,
            String name,
            String category,
            UUID legId,
            String flightNo,
            String registration,
            String severity,
            Integer risk,
            String method,
            String why,
            String impact,
            String action,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime lastSeenAt) implements Serializable {
    }

    /** Les quatre compteurs de {@code ALERTS.counts()}. */
    public record VigilCountsDto(int critical, int high, int warning, int info) implements Serializable {
        public int total() {
            return critical + high + warning + info;
        }
    }

    /**
     * Le vol tel que le balayage l'a lu — ce que l'annexe garde dans
     * {@code CORE.lastCtxs} et que l'agent interroge.
     */
    public record VigilFlightDto(
            UUID legId,
            String flightNo,
            String registration,
            String depIcao,
            String arrIcao,
            OffsetDateTime std,
            OffsetDateTime etd,
            OffsetDateTime eta,
            String status,
            int riskScore,
            String riskLevel,
            List<VigilContributorDto> contributors,
            List<String> facts,
            List<String> recommendations) implements Serializable {
    }

    /** Une categorie et sa part du score — la « contribution » du moteur de risque. */
    public record VigilContributorDto(String category, int points, int share) implements Serializable {
    }

    /**
     * Le panneau entier, en une lecture.
     *
     * @param state     ACTIVE, WARNING ou CRITICAL — le mot a cote du titre
     * @param monitored le nombre de vols que le balayage a lus
     * @param scannedAt l'instant du balayage qui a produit ces lignes
     */
    public record VigilPanelDto(
            String state,
            VigilCountsDto counts,
            int monitored,
            OffsetDateTime scannedAt,
            List<VigilAlertDto> alerts,
            List<VigilFlightDto> flights,
            VigilFleetDto fleet) implements Serializable {
    }

    /** {@code FLEET.snapshot()} + {@code capacityForecast()}, la part que nos donnees portent. */
    public record VigilFleetDto(
            int tails,
            List<String> aog,
            List<String> maintenance,
            List<String> idle,
            int demandSectors,
            int availableTails,
            int horizonHours,
            String capacityLevel,
            List<String> capacityRisks,
            List<VigilUtilisationDto> utilisation) implements Serializable {
    }

    /** Heures bloc du jour par type, contre la capacite de dix heures par appareil disponible. */
    public record VigilUtilisationDto(String fleet, int tails, double blockHours, int capacityHours,
                                      int percentOfCapacity) implements Serializable {
    }

    public record AskVigilCommand(@NotBlank String question) {
    }

    public record VigilAnswerDto(String answer, OffsetDateTime answeredAt) implements Serializable {
    }

    public record SetVigilAlertStatusCommand(@NotNull String status) {
    }
}
