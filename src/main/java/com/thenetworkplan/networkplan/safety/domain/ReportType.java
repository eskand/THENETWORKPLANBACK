package com.thenetworkplan.networkplan.safety.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * What kind of thing is being reported.
 *
 * <p>The ten of the approved prototype, offered to every function of the
 * company — flight and cabin crew, dispatch, OCC, maintenance, ground
 * operations. The form is not the same for a near miss as for a fatigue
 * report, and a single "safety report" would ask everyone the wrong questions.
 *
 * <p>Each maps onto an {@link OccurrenceCategory}, which is what the Safety
 * Manager analyses and what is exported to the authority. The two are kept
 * apart on purpose: the reporter picks the words that fit what they saw, and
 * the classification the regulator expects is derived from it rather than
 * demanded of them.
 */
public enum ReportType {

    INCIDENT("Incident report", "Unplanned events",
            "An event that affected or could have affected the safety of the operation.",
            OccurrenceCategory.OPERATIONAL),

    HAZARD("Hazard report", "Harm potential",
            "A condition with the potential to cause harm, whether or not anything happened.",
            OccurrenceCategory.OPERATIONAL),

    NEAR_MISS("Near miss", "Avoided incidents",
            "Something that almost happened and was avoided.",
            OccurrenceCategory.OPERATIONAL),

    OBSERVATION("Safety observation", "Safety concerns",
            "Something you noticed that the operator should know about.",
            OccurrenceCategory.OTHER),

    FATIGUE("Fatigue report", "Fitness for duty",
            "Fatigue affecting, or likely to affect, your fitness for duty.",
            OccurrenceCategory.OPERATIONAL),

    TECHNICAL("Technical / airworthiness", "Defects and MEL",
            "A defect, MEL concern or maintenance issue with a safety dimension.",
            OccurrenceCategory.TECHNICAL),

    GROUND("Ground safety", "Ramp events",
            "Ramp, loading, fuelling, de-icing or equipment event.",
            OccurrenceCategory.GROUND),

    DISPATCH("Dispatch / flight planning", "Planning and slots",
            "Flight plan, NOTAM, fuel, load sheet or slot issue.",
            OccurrenceCategory.OPERATIONAL),

    SECURITY("Security", "Unlawful interference",
            "Unlawful interference, access control or overflight security concern.",
            OccurrenceCategory.SECURITY),

    REX("Experience feedback", "Lessons learned",
            "A lesson worth sharing, including something that went well.",
            OccurrenceCategory.OTHER);

    private final String label;
    private final String shortLabel;
    private final String description;
    private final OccurrenceCategory category;

    ReportType(String label, String shortLabel, String description, OccurrenceCategory category) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.description = description;
        this.category = category;
    }

    public String label() {
        return label;
    }

    public String shortLabel() {
        return shortLabel;
    }

    public String description() {
        return description;
    }

    /** The class the Safety Manager and the authority work in. */
    public OccurrenceCategory category() {
        return category;
    }

    /**
     * Whether this kind of report is normally filed by the person it concerns.
     *
     * <p>A fatigue report is about the reporter's own fitness, so it is the one
     * type where anonymity removes the possibility of acting: nobody can be
     * taken off a duty they cannot be identified with. The form says so rather
     * than refusing the choice.
     */
    public boolean concernsTheReporter() {
        return this == FATIGUE;
    }

    public static Optional<ReportType> of(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(type -> type.name().equalsIgnoreCase(name.trim()))
                .findFirst();
    }
}
