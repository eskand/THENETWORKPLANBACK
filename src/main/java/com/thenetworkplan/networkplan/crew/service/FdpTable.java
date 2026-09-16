package com.thenetworkplan.networkplan.crew.service;

import java.util.List;

/**
 * ORO.FTL.205(b)(1) — maximum daily flight duty period, acclimatised crew.
 *
 * <p><b>The table is the regulation, transcribed, not a formula.</b> Table 2 of
 * Regulation (EU) No 965/2012 Subpart FTL is not derivable: the 13:30 band
 * loses fifteen minutes, the 14:00 band another fifteen, and the early-morning
 * bands climb back in quarter-hours. Any code that tried to interpolate it
 * would be right in the middle of the table and wrong at both ends, which is
 * the worst possible failure for a limit.
 *
 * <p><b>Reporting time is the local time at the place of reporting.</b> This
 * class takes minutes-of-day and asks no questions about where they came from:
 * the caller holds the base and the time zone, and an FDP table that guessed at
 * acclimatisation would be answering a different question than the one asked.
 *
 * <p><b>Acclimatisation state D is treated as B here.</b> The state machine of
 * Table 1 belongs to a rostering engine that knows each crew member's last
 * seventy-two hours; this class answers the acclimatised case, which is the one
 * a report over a published roster can defend, and says so in {@link
 * Fdp#reference()} so nobody quotes it as more.
 */
public final class FdpTable {

    private FdpTable() {
    }

    /** One band of Table 2: a window of reporting times and its nine columns. */
    private record Band(String label, int fromMinute, int toMinute, List<String> values) {
    }

    private static final List<Band> TABLE_2 = List.of(
            new Band("06:00–13:29", 360, 809,
                    List.of("13:00", "12:30", "12:00", "11:30", "11:00", "10:30", "10:00", "09:30", "09:00")),
            new Band("13:30–13:59", 810, 839,
                    List.of("12:45", "12:15", "11:45", "11:15", "10:45", "10:15", "09:45", "09:15", "09:00")),
            new Band("14:00–14:29", 840, 869,
                    List.of("12:30", "12:00", "11:30", "11:00", "10:30", "10:00", "09:30", "09:00", "09:00")),
            new Band("14:30–14:59", 870, 899,
                    List.of("12:15", "11:45", "11:15", "10:45", "10:15", "09:45", "09:15", "09:00", "09:00")),
            new Band("15:00–15:29", 900, 929,
                    List.of("12:00", "11:30", "11:00", "10:30", "10:00", "09:30", "09:00", "09:00", "09:00")),
            new Band("15:30–15:59", 930, 959,
                    List.of("11:45", "11:15", "10:45", "10:15", "09:45", "09:15", "09:00", "09:00", "09:00")),
            new Band("16:00–16:29", 960, 989,
                    List.of("11:30", "11:00", "10:30", "10:00", "09:30", "09:00", "09:00", "09:00", "09:00")),
            new Band("16:30–16:59", 990, 1019,
                    List.of("11:15", "10:45", "10:15", "09:45", "09:15", "09:00", "09:00", "09:00", "09:00")),
            new Band("17:00–04:59", 1020, 1439,
                    List.of("11:00", "10:30", "10:00", "09:30", "09:00", "09:00", "09:00", "09:00", "09:00")),
            new Band("17:00–04:59", 0, 299,
                    List.of("11:00", "10:30", "10:00", "09:30", "09:00", "09:00", "09:00", "09:00", "09:00")),
            new Band("05:00–05:14", 300, 314,
                    List.of("12:00", "11:30", "11:00", "10:30", "10:00", "09:30", "09:00", "09:00", "09:00")),
            new Band("05:15–05:29", 315, 329,
                    List.of("12:15", "11:45", "11:15", "10:45", "10:15", "09:45", "09:15", "09:00", "09:00")),
            new Band("05:30–05:44", 330, 344,
                    List.of("12:30", "12:00", "11:30", "11:00", "10:30", "10:00", "09:30", "09:00", "09:00")),
            new Band("05:45–05:59", 345, 359,
                    List.of("12:45", "12:15", "11:45", "11:15", "10:45", "10:15", "09:45", "09:15", "09:00")));

    /** The rolling duty ceilings of ORO.FTL.210(a), in minutes. */
    public static final int DUTY_CEILING_7_DAYS_MINUTES = 60 * 60;
    public static final int DUTY_CEILING_28_DAYS_MINUTES = 190 * 60;

    /** The block ceilings of ORO.FTL.210(b), in minutes. */
    public static final int BLOCK_CEILING_28_DAYS_MINUTES = 100 * 60;
    public static final int BLOCK_CEILING_YEAR_MINUTES = 900 * 60;

    /**
     * The maximum FDP, and the clause it comes from.
     *
     * @param minutes   the ceiling in minutes
     * @param reference what to cite when the figure is questioned
     */
    public record Fdp(int minutes, String reference) {
    }

    /**
     * Maximum daily FDP for a report time and a sector count.
     *
     * @param reportMinuteOfDay minutes past midnight, local to the reporting point
     * @param sectors           sectors in the duty; one or more. A duty recorded
     *                          with zero sectors is read as one rather than
     *                          rejected: the column for « no sectors » does not
     *                          exist, and the 1–2 column is the least generous
     *                          honest answer.
     */
    public static Fdp maxFdp(int reportMinuteOfDay, int sectors) {
        int minute = Math.floorMod(reportMinuteOfDay, 1440);
        int column = sectors <= 2 ? 0 : Math.min(sectors - 2, 8);
        Band band = TABLE_2.stream()
                .filter(row -> minute >= row.fromMinute() && minute <= row.toMinute())
                .findFirst()
                .orElse(TABLE_2.get(0));
        return new Fdp(minutes(band.values().get(column)),
                "ORO.FTL.205(b)(1) / Table 2 (band " + band.label() + ")");
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3));
    }
}
