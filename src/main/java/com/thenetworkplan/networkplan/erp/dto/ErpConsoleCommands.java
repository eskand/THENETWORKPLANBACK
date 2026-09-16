package com.thenetworkplan.networkplan.erp.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/** What the console can change. */
public final class ErpConsoleCommands {

    private ErpConsoleCommands() {
    }

    /**
     * Ask the plan what level a situation is.
     *
     * <p>Answers may be partial — the console shows a running verdict as the
     * questions are worked through, and an assessment stopped halfway is still
     * worth seeing.
     */
    public record AssessCommand(
            String eventCode,
            Map<String, String> answers) {
    }

    /** Tick or untick one action of the plan. */
    public record CheckCommand(
            @NotBlank String itemCode,
            @NotNull Boolean done,
            /** Who did it. A tick with no name proves nothing. */
            @NotBlank String actor) {
    }

    /** Record that a statutory notification has been made. */
    public record NotifyCommand(
            @NotBlank String typeCode,
            @NotBlank String actor,
            String channel,
            String reference) {
    }

    /** Move the crisis up or down a level, with the reason on the record. */
    public record LevelCommand(
            @NotNull @Min(0) @Max(4) Short level,
            @NotBlank String reason,
            @NotBlank String actor) {
    }

    public record SitrepCommand(
            @NotBlank String body,
            @NotBlank String author) {
    }

    /** The aircraft the crisis is about. Every field optional: it arrives piecemeal. */
    public record SubjectCommand(
            String flight,
            String registration,
            String aircraftType,
            String origin,
            String destination,
            String pob,
            String dangerousGoods,
            String lastPosition,
            String squawk,
            String fuelState,
            String souls,
            @NotBlank String actor) {
    }

    /** A line written straight into the crisis log. */
    public record LogCommand(
            @NotBlank String text,
            String actor) {
    }
}
