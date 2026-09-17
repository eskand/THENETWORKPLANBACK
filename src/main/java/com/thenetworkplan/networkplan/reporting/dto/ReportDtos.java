package com.thenetworkplan.networkplan.reporting.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** The read models of the Reports screen. */
public final class ReportDtos {

    private ReportDtos() {
    }

    public record ReportDefinitionDto(
            UUID id,
            String code,
            String title,
            String domain,
            String description,
            /** Flights, Crew, Maintenance, Sales, Safety — how the left menu groups them. */
            String module,
            /** One line saying what the report answers. */
            String subtitle,
            /** How the fleet and period filters apply to this report. */
            String scope,
            /** Where it sits in the left menu, low first. */
            int menuOrder,
            /** False when no runner answers this code: the screen says so rather than
             *  showing an empty table. */
            boolean runnable,
            int defaultWindowDays,
            OffsetDateTime lastRunAt,
            Integer lastRowCount) implements Serializable {
    }

    /**
     * One headline figure of a report.
     *
     * <p>The prototype puts six of these above every report, and they are not
     * decoration: a table of two hundred rows answers nothing until somebody
     * has read it, and the KPI is the sentence the table supports.
     */
    public record ReportKpiDto(
            String label,
            String value,
            String sub,
            /** neutral, good, warn or bad — what the figure says about itself. */
            String tone,
            /** 0-100 when the figure has a natural denominator, else null. */
            Integer bar) implements Serializable {
    }

    /**
     * One chart of a report.
     *
     * <p>Series and labels, not a rendered image: the browser draws it, and a
     * chart the server rendered could not be resized, re-themed or read by a
     * screen reader.
     */
    public record ReportChartDto(
            String id,
            String title,
            /** bar, hbar, line, donut or stacked. */
            String kind,
            /** half, third, twothirds or full — the width the prototype gives it. */
            String width,
            List<String> labels,
            List<ReportSeriesDto> series) implements Serializable {
    }

    public record ReportSeriesDto(
            String label,
            List<Double> data,
            String colour,
            /**
             * One colour per point, when the points are categories rather than a
             * progression.
             *
             * <p>A donut of flight statuses is not a gradient: cancelled is red
             * wherever it lands in the list, and colouring by index would move it
             * every time a status disappears from the period. Null when the series
             * is one colour.
             */
            List<String> colours) implements Serializable {

        public ReportSeriesDto(String label, List<Double> data, String colour) {
            this(label, data, colour, null);
        }
    }

    /**
     * The answer to one report, as a table.
     *
     * <p>Columns and rows rather than a typed record per report: a report is a
     * table an operator exports, and typing forty of them would freeze the
     * catalogue in Java. Every value is already formatted by the runner, so
     * the browser only renders.
     */
    public record ReportResultDto(
            String code,
            String title,
            String domain,
            LocalDate windowFrom,
            LocalDate windowTo,
            List<String> columns,
            List<List<String>> rows,
            int rowCount,
            long durationMs,
            /** The headline figures, above the table. */
            List<ReportKpiDto> kpis,
            List<ReportChartDto> charts,
            /** What the figures mean, and what they deliberately do not. */
            String note,
            OffsetDateTime computedAt) implements Serializable {
    }

    public record ReportRunDto(
            UUID id,
            String code,
            String title,
            OffsetDateTime ranAt,
            LocalDate windowFrom,
            LocalDate windowTo,
            int rowCount,
            Integer durationMs) implements Serializable {
    }
}
