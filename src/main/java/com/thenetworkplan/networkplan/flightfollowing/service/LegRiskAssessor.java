package com.thenetworkplan.networkplan.flightfollowing.service;

import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;

/**
 * The one place a leg is scored against the SMS matrix.
 *
 * <p><b>Why it is not a private method any more.</b> Flight Following computed
 * this; the Dispatch board read a stored {@code ops.legs.risk_level} column
 * that nothing in the application writes. So the flight file opened from the
 * timeline and the flight file opened from the dispatch desk could show two
 * different risk bands for the same leg — and a dispatcher has no way of
 * knowing which of the two to believe. ICAO Doc 9859 asks an operator to hold
 * one risk picture, not one per screen.
 *
 * <p>It reads nothing itself: the caller passes the MEL item and the crew
 * picture it already holds, so folding the assessment into a forty-row board
 * costs no extra query.
 */
public interface LegRiskAssessor {

    /**
     * Scores the five factors of {@link SmsRiskRule} for one leg.
     *
     * @param mel  the open MEL item that matters most on the aircraft, or null
     * @param crew the crew picture for the leg, or null when none is assigned
     */
    SmsRiskRule.Assessment assess(MelItemDto mel, LegCrewDto crew);
}
