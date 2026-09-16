package com.thenetworkplan.networkplan.safety.dto;

import java.util.UUID;

/** Acknowledgements of one campaign, counted by the database. */
public record CampaignCount(UUID campaignId, Long count) {

    public int intValue() {
        return count == null ? 0 : count.intValue();
    }
}
