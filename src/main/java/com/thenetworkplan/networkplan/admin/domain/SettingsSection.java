package com.thenetworkplan.networkplan.admin.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * The rubrics down the left of the Settings screen.
 *
 * <p>The nine schema-driven ones, their order and their wording are the
 * prototype's ({@code SETTINGS_SCHEMA}, annexe A4 l. 66172-66338). Two are
 * added at the end, {@link #MAINTENANCE} and {@link #SALES}, because two
 * settings this application already reads for real — the CAMO due-list warning
 * windows and the pipeline currency — belong to no rubric the prototype has.
 * Dropping a live setting to match a screenshot would be the wrong trade.
 *
 * <p>Titles and blurbs live here rather than in the table because they are
 * product wording, not operator data: an operator configures the values, not
 * the sentence that explains what the rubric is for. The values, their
 * controls and their options are in {@code platform.settings}.
 */
public enum SettingsSection {

    GENERAL("general", "General & OCC",
            "Operator identity, reference time zone, units and the platform-wide display preferences."),

    ALERTS("alerts", "Alerts & Notifications",
            "What raises an OCC alert, and where it goes. This is where you decide what deserves "
                    + "the dispatcher's attention."),

    TIMELINE("timeline", "Flight Timeline",
            "How the Gantt behaves: visible time span, refresh rate and the status colour codes."),

    DISPATCH("dispatch", "Dispatch / OCC",
            "The central dispatch desk: default services, reserves and how a flight package is "
                    + "cleared for release."),

    FOLLOWING("following", "Flight Following",
            "Live tracking: position source, refresh rate and what starts the flight watch."),

    CREW("crew", "Crew & FTL",
            "Flight and duty time limitations (ORO.FTL) and crew planning preferences."),

    SAFETY("safety", "Safety (SMS)",
            "Safety management: the risk matrix, the occurrence report workflow and review reminders."),

    AIRPORTS("airports", "Airports Data",
            "Aerodrome suitability criteria and categorisation, used when a flight package is checked."),

    DATA("data", "Data & Backup",
            "Local persistence, configuration backup and restore, and resetting the platform.",
            true, null),

    OPSQUAL("opsqual", "OPS Qualifications",
            "Low-visibility approach category per pilot (CAT I to CAT IIIC). Editable here and in "
                    + "Crew Management.",
            false, "OPS_QUALIFICATIONS"),

    ADMINISTRATION("administration", "Administration",
            "Users, the company organisation, roles, the permission matrix, groups, the activity log "
                    + "and the authentication policy.",
            false, "ADMINISTRATION"),

    /* --- the two this application needs and the prototype has not --- */

    MAINTENANCE("maintenance", "Maintenance (CAMO)",
            "When the due list starts warning, in days and in flight hours, ahead of a limit."),

    SALES("sales", "Sales & CRM",
            "Commercial defaults: the currency the pipeline is reported in.");

    private final String id;
    private final String title;
    private final String blurb;
    /** Backup, restore and reset buttons are appended under this rubric's fields. */
    private final boolean actions;
    /** A rubric whose body is another screen entirely, not a list of fields. */
    private final String custom;

    SettingsSection(String id, String title, String blurb) {
        this(id, title, blurb, false, null);
    }

    SettingsSection(String id, String title, String blurb, boolean actions, String custom) {
        this.id = id;
        this.title = title;
        this.blurb = blurb;
        this.actions = actions;
        this.custom = custom;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String blurb() {
        return blurb;
    }

    public boolean hasActions() {
        return actions;
    }

    public String custom() {
        return custom;
    }

    /** Empty rather than throwing: an unknown section in the table is a data problem, not a crash. */
    public static Optional<SettingsSection> of(String name) {
        return Arrays.stream(values()).filter(section -> section.name().equals(name)).findFirst();
    }
}
