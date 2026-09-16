package com.thenetworkplan.networkplan.reporting.service;

import java.time.LocalDate;
import java.util.List;
import com.thenetworkplan.networkplan.reporting.dto.ReportDtos;
import java.util.UUID;

/**
 * One report, one runner.
 *
 * <p>A runner asks the owning module's <em>service</em> for its figures — it
 * never reads another schema. Adding a report is adding one implementation of
 * this interface; nothing else in the module changes, and Spring collects them
 * by their {@link #code()}.
 */
public interface ReportRunner {

    /** The definition code this runner answers, e.g. {@code OPS-UTIL}. */
    String code();

    List<String> columns();

    /** One row per line of the report, values already formatted. */
    List<List<String>> rows(UUID tenantId, LocalDate from, LocalDate to);

    /**
     * What the figures mean, and what they deliberately do not.
     *
     * <p>Shown under the table. A report that does not say what it excludes is
     * a report somebody will misread.
     */
    String note();

    /**
     * The headline figures, above the table.
     *
     * <p>Default empty: a report is a table first, and a runner that has
     * nothing worth putting in a KPI card should not be made to invent one.
     */
    default List<ReportDtos.ReportKpiDto> kpis(UUID tenantId, LocalDate from, LocalDate to) {
        return List.of();
    }

    /** The charts, drawn by the browser from labels and series. */
    default List<ReportDtos.ReportChartDto> charts(UUID tenantId, LocalDate from, LocalDate to) {
        return List.of();
    }

    /** The module the report belongs to: Flights, Maintenance, Crew, Sales, Safety. */
    default String module() {
        return "Flights";
    }

    /** One line under the title, saying what the report actually answers. */
    default String subtitle() {
        return null;
    }

    /** How the fleet and period filters apply to this report. */
    default String scope() {
        return null;
    }
}
