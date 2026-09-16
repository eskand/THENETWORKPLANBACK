package com.thenetworkplan.networkplan.crew.service;

import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import java.util.Locale;

/**
 * The aircraft family a crew member is spoken of by: FALCON, CITATION, LEGACY.
 *
 * <p>A crew planner does not say "a FA7X captain", they say "a Falcon captain":
 * the rating that matters on a roster line is the family, because that is the
 * granularity at which a person can be swapped onto another tail. The approved
 * prototype labels its rows the same way.
 *
 * <p>The family is the first word of the published model — "Falcon 7X" and
 * "Falcon 900LX" are both FALCON — and never a table of hand-written mappings:
 * a new type added to {@code refdata.aircraft_types} gets its family for free,
 * and no one has to remember to extend a switch.
 *
 * <p>It lives here, alone and without I/O, because two screens need the same
 * answer: the roster grid and the crew scheduling pool. Two copies of this rule
 * would end up disagreeing, and a captain would read FALCON on one screen and
 * CITATION on the other.
 */
public final class TypeFamily {

    private TypeFamily() {
    }

    /** Null when there is nothing to read: a missing rating is not a family. */
    public static String of(AircraftType type) {
        return type == null ? null : of(type.getModel(), type.getIcaoType());
    }

    /**
     * @param model    the published model, "Falcon 900LX"
     * @param icaoType the ICAO designator, used only when no model is on file
     */
    public static String of(String model, String icaoType) {
        String source = model == null || model.isBlank() ? icaoType : model.trim().split("\\s+")[0];
        if (source == null || source.isBlank()) {
            return null;
        }
        return source.trim().toUpperCase(Locale.ROOT);
    }
}
