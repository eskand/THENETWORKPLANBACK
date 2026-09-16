package com.thenetworkplan.networkplan.flightfollowing.dto;

import com.thenetworkplan.networkplan.flightfollowing.service.AdsbIngestService;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record FollowingBoardDto(
        LocalDate date,
        List<FollowedFlightDto> airborne,
        List<FollowedFlightDto> upcoming,
        List<FollowedFlightDto> arrived,
        int trackedLive,
        int trackedStale,
        int withoutSource,
        int staleThresholdMinutes,
        /** Les quatre compteurs du bandeau SMS, comptes sur les vols affiches. */
        int riskLow,
        int riskMedium,
        int riskHigh,
        int riskCritical,
        /**
         * Ce que la derniere lecture ADS-B a donne : etat de la source,
         * combien d appareils elle a vus, combien appartiennent a la flotte,
         * et lesquels des notres ne peuvent pas etre correles faute de code
         * Mode-S. L ecran peut ainsi expliquer une carte vide au lieu de la
         * laisser passer pour une panne.
         */
        AdsbIngestService.Result adsb,
        OffsetDateTime computedAt) implements Serializable {
}
