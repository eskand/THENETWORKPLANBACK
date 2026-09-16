package com.thenetworkplan.networkplan.weather.service;

import java.util.Map;
import java.util.Set;

/**
 * Where raw METARs come from.
 *
 * <p>An interface with one implementation today (NOAA, public and keyless)
 * and an obvious second one tomorrow (the AVWX relay of annexe A4, once its
 * token lives in an environment variable rather than in a source file — the
 * audit's first finding).
 *
 * <p>The source returns raw text only. Decoding is {@code MetarParser}'s job,
 * and storing is the service's: a provider that could write to the database
 * would make its data indistinguishable from an operator's.
 */
public interface MetarSource {

    /** Name recorded on every row this source produces: NOAA, AVWX… */
    String provider();

    boolean isEnabled();

    /**
     * @return station to raw message, missing for a station that answered
     *         nothing — an absent entry is an answer, and the caller shows it
     */
    Map<String, String> fetch(Set<String> stationIcaoCodes);
}
