package com.thenetworkplan.networkplan.tripsupport.domain;

/**
 * The ground services a leg can carry, as the flight label offers them.
 *
 * <p>The list is the A4 annexe's {@code SERVICE_TYPES_BASE} (prototype
 * l. 12644) plus the two that the United Kingdom stations add to it, GAR and
 * FCP. The order below is the order the annexe's dropdown shows, because that
 * dropdown is what a dispatcher reads: changing it would move the item under
 * the cursor without changing anything else.
 */
public enum GroundServiceType {
    HANDLING,
    FUEL,
    CUSTOMS,
    CATERING,
    PARKING,
    DEICING,
    CREW_TRANSPORT,
    PPR,
    APIS,
    AIRPORT_SLOT,
    LANDING_PERMIT,
    PAX_TRANSPORT,
    GAR,
    FCP
}
