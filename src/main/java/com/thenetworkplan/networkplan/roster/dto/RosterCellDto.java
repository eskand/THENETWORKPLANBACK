package com.thenetworkplan.networkplan.roster.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One code on one day.
 *
 * <p>{@code backed} says whether the cell rests on a recorded duty period. A
 * plan and a fact look different on the grid on purpose: the prototype drew
 * them identically, and a crew planner could not tell what had actually
 * happened from what had merely been intended.
 */
public record RosterCellDto(
        UUID entryId,
        LocalDate day,
        String code,
        boolean backed,
        UUID legId,
        String remark,
        /**
         * True when the cell comes from a draft version.
         *
         * <p>The month grid lays several versions side by side, and a planner
         * has to tell a commitment from an intention at a glance: the same
         * distinction the weekly grid draws with a dashed outline. Inside a
         * single version this is constant, and the screen ignores it.
         */
        boolean draft) implements Serializable {
}
