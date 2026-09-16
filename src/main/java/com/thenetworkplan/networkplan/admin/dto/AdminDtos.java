package com.thenetworkplan.networkplan.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The read models of the Settings and Database screens. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record SettingDto(
            UUID id,
            String category,
            String settingKey,
            String settingValue,
            String defaultValue,
            String valueType,
            String unit,
            String label,
            String description,
            /** The service that reads this key, or null when nothing reads it yet. */
            String readBy,
            boolean editable,
            /** TEXT, NUMBER, SELECT, SEGMENT, TOGGLE, COLOR, TAGS, TIME. */
            String control,
            List<SettingOptionDto> options,
            String placeholder,
            BigDecimal minValue,
            BigDecimal maxValue,
            BigDecimal stepValue,
            String showIfKey,
            String showIfValue,
            OffsetDateTime updatedAt) implements Serializable {

        /** True when the row is at the value it was seeded with. */
        public boolean atDefault() {
            return settingValue.equals(defaultValue);
        }
    }

    public record SettingOptionDto(String value, String label) implements Serializable {
    }

    /** One block of fields inside a rubric: "Operations centre", "Units". */
    public record SettingsGroupDto(String title, List<SettingDto> fields) implements Serializable {
    }

    /**
     * One rubric of the Settings screen.
     *
     * <p>{@code custom} names a screen that replaces the field list entirely —
     * OPS qualifications and Administration are not rows with controls, and
     * squeezing them into that shape would help nobody.
     *
     * <p>{@code unreadCount} is the honest number: fields in this rubric that
     * no service consumes yet. The screen says so rather than presenting a knob
     * that does nothing.
     */
    public record SettingsSectionDto(
            String id,
            String name,
            String title,
            String blurb,
            boolean actions,
            String custom,
            int fieldCount,
            int unreadCount,
            List<SettingsGroupDto> groups) implements Serializable {
    }

    public record SettingsFormDto(
            List<SettingsSectionDto> sections,
            int total,
            int withReader) implements Serializable {
    }

    /**
     * One tail on the Fleet Register.
     *
     * <p>Every figure comes from the type's reference record, not from a value
     * typed against the tail: two aircraft of the same type cannot disagree
     * about how long a runway they need.
     */
    public record FleetRegisterRowDto(
            UUID aircraftId,
            String registration,
            String icaoType,
            String model,
            String referenceModel,
            String engines,
            Integer mtowKg,
            /**
             * Doc 8643 certified maximum persons on board.
             *
             * <p>Not the cabin: a Falcon 2000 is certified for nineteen and this
             * operator's is fitted with ten. The prototype shows this figure, so
             * this column does too — with {@link #cabinSeats} beside it, because a
             * fleet register that says 108 for a nineteen-seat VIP conversion is
             * accurate and useless at the same time.
             */
            Integer maxPersons,
            /** Seats installed per the reference. */
            Integer seats,
            /** What the operator's own cabin is configured for. */
            Integer cabinSeats,
            Integer rangeNm,
            Integer cruiseTasKt,
            Integer minRunwayM,
            String crewConfig,
            String crewCertification,
            String status,
            String statusReason) implements Serializable {
    }

    /** One family block of the Fleet Register — Falcon, Citation, Legacy, Lineage. */
    public record FleetFamilyDto(String family, String label, List<FleetRegisterRowDto> aircraft)
            implements Serializable {
    }

    public record FleetRegisterDto(
            List<FleetFamilyDto> families,
            int total,
            int serviceable,
            int outOfService) implements Serializable {
    }

    /** One record of the 308-type aircraft reference. */
    public record AircraftReferenceDto(
            UUID id,
            String manufacturer,
            String model,
            String icaoType,
            String descriptor,
            String wakeCategory,
            String wakeSource,
            Integer mtowKg,
            Integer mzfwKg,
            Integer mlwKg,
            Integer dowKg,
            Integer fuelKg,
            Integer fuelL,
            Integer seats,
            String pob,
            Integer maxPersons,
            Integer rangeNm,
            Integer rangeMaxPayloadNm,
            Integer rangeFullPaxNm,
            Integer rangeMaxFuelNm,
            BigDecimal mach,
            Integer cruiseTasKt,
            Integer optimumFl,
            Integer takeoffDistanceM,
            Integer rffsCategory,
            List<Integer> etopsMinutes) implements Serializable {
    }

    public record UpdateSettingCommand(@NotBlank String settingValue) {
    }

    /** A whole configuration file being restored: key to value, nothing else. */
    public record ImportSettingsCommand(Map<String, String> values) {
    }

    /**
     * One reference set on the Database screen.
     *
     * <p>{@code rowCount} and {@code lastUpdatedAt} are queried, not declared:
     * a reference set that has not moved in two years is visible as such, and
     * an empty one cannot pretend to be loaded.
     */
    public record ReferenceSetDto(
            String code,
            String label,
            String schemaName,
            String tableName,
            String ownedBy,
            long rowCount,
            OffsetDateTime lastUpdatedAt,
            String sourceOfTruth,
            /** What breaks if the set is empty or stale. */
            String usedFor) implements Serializable {
    }

    public record DatabaseBoardDto(
            List<ReferenceSetDto> sets,
            long totalRows,
            int emptySets,
            OffsetDateTime computedAt) implements Serializable {
    }
}
