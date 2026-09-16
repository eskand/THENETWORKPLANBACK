package com.thenetworkplan.networkplan.safety.domain;

/** Family of the occurrence, as the operator classifies it. */
public enum OccurrenceCategory {
    TECHNICAL,
    OPERATIONAL,
    GROUND,
    CABIN,
    SECURITY,
    MEDICAL,
    ATC,
    WEATHER,
    BIRD_STRIKE,
    FUEL,
    OTHER
}
