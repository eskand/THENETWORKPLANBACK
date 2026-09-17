package com.thenetworkplan.networkplan.flightfollowing.service.impl;

import com.thenetworkplan.networkplan.airworthiness.dto.MelItemDto;
import com.thenetworkplan.networkplan.crew.dto.LegCrewDto;
import com.thenetworkplan.networkplan.flightfollowing.service.LegRiskAssessor;
import com.thenetworkplan.networkplan.flightfollowing.service.SmsRiskRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The SMS scoring of a leg, moved out of Flight Following so the Dispatch board
 * can read the same verdict.
 *
 * <p>The body is unchanged from where it lived: three factors are read (MEL,
 * FTL, crew), two are not yet attached to the leg (destination weather, NOTAM
 * digest). Those two are scored UNKNOWN rather than NONE — the prototype read
 * an absent source as good news, which is the most dangerous default there is.
 */
@Service
public class LegRiskAssessorImpl implements LegRiskAssessor {

    private final SmsRiskRule smsRiskRule;

    public LegRiskAssessorImpl(SmsRiskRule smsRiskRule) {
        this.smsRiskRule = smsRiskRule;
    }

    @Override
    public SmsRiskRule.Assessment assess(MelItemDto mel, LegCrewDto crew) {
        List<SmsRiskRule.Scored> factors = new ArrayList<>(5);

        factors.add(new SmsRiskRule.Scored(SmsRiskRule.Factor.WEATHER,
                SmsRiskRule.Level.UNKNOWN,
                "Destination weather not yet attached to the leg"));
        factors.add(new SmsRiskRule.Scored(SmsRiskRule.Factor.NOTAM,
                SmsRiskRule.Level.UNKNOWN,
                "NOTAM digest not yet attached to the leg"));

        String ftl = crew == null ? null : crew.ftlStatus();
        factors.add(new SmsRiskRule.Scored(SmsRiskRule.Factor.FTL,
                switch (ftl == null ? "UNKNOWN" : ftl) {
                    case "OK" -> SmsRiskRule.Level.NONE;
                    case "WARNING" -> SmsRiskRule.Level.MODERATE;
                    case "BREACH" -> SmsRiskRule.Level.SEVERE;
                    default -> SmsRiskRule.Level.UNKNOWN;
                },
                ftl == null ? "No crew assigned to this leg" : "Crew FTL: " + ftl.toLowerCase()));

        factors.add(new SmsRiskRule.Scored(SmsRiskRule.Factor.MEL,
                mel == null ? SmsRiskRule.Level.NONE
                        : mel.blocksDispatch() ? SmsRiskRule.Level.MAJOR : SmsRiskRule.Level.MINOR,
                mel == null ? "No open MEL item"
                        : mel.reference() + (mel.blocksDispatch()
                                ? " — major MEL, degraded" : " — minor MEL")));

        String documents = crew == null ? null : crew.documentStatus();
        boolean incomplete = crew != null && !crew.complete();
        factors.add(new SmsRiskRule.Scored(SmsRiskRule.Factor.CREW,
                crew == null ? SmsRiskRule.Level.UNKNOWN
                        : "EXPIRED".equals(documents) ? SmsRiskRule.Level.MAJOR
                        : incomplete ? SmsRiskRule.Level.MODERATE
                        : SmsRiskRule.Level.NONE,
                crew == null ? "No crew record for this leg"
                        : "EXPIRED".equals(documents) ? "A crew document has expired"
                        : incomplete ? crew.seatsFilled() + " of " + crew.minimumSeats()
                                + " flight-deck seats filled"
                        : "Crew complete and current"));

        return smsRiskRule.assess(factors);
    }
}
