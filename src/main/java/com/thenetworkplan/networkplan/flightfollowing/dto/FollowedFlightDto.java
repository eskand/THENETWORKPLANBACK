package com.thenetworkplan.networkplan.flightfollowing.dto;

import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One flight being followed.
 *
 * <p>{@code tracking} is the honest part: {@code NO_SOURCE} when no position
 * has ever been received for the leg, {@code STALE} when the last one is older
 * than the operator threshold, {@code LIVE} otherwise. The prototype drew a
 * moving aircraft in all three cases.
 */
public record FollowedFlightDto(
        UUID legId,
        String flightNo,
        String registration,
        String icaoType,
        String model,
        /** L exploitant qui porte le vol. */
        String operator,
        String depIcao,
        String arrIcao,
        OffsetDateTime std,
        OffsetDateTime sta,
        OffsetDateTime etaRevised,
        String status,
        /** LIVE / STALE / NO_SOURCE */
        String tracking,
        PositionDto lastPosition,
        Long minutesToDestination,
        Integer progressPercent,
        /** Niveau de vol, deduit de l altitude de la derniere position. */
        Integer flightLevel,
        String melReference,
        boolean melBlocking,
        /**
         * L evaluation SMS, calculee par SmsRiskRule sur les memes faits que
         * les autres ecrans lisent. Jamais null : un vol qu on ne sait pas
         * evaluer porte un niveau LOW assorti de la liste des sources muettes,
         * pas une absence d evaluation qui se lirait comme une absence de
         * risque.
         */
        SmsRiskRule.Assessment risk) implements Serializable {
}
