package com.thenetworkplan.networkplan.admin.service.impl;

import com.thenetworkplan.networkplan.admin.domain.Setting;
import com.thenetworkplan.networkplan.admin.domain.SettingsSection;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.AircraftReferenceDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.DatabaseBoardDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.FleetFamilyDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.FleetRegisterDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.FleetRegisterRowDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.ReferenceSetDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingOptionDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingsFormDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingsGroupDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.SettingsSectionDto;
import com.thenetworkplan.networkplan.admin.dto.AdminDtos.UpdateSettingCommand;
import com.thenetworkplan.networkplan.admin.repository.SettingRepository;
import com.thenetworkplan.networkplan.admin.service.AdminService;
import com.thenetworkplan.networkplan.airworthiness.domain.Aircraft;
import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.crew.service.TypeFamily;
import com.thenetworkplan.networkplan.refdata.domain.AircraftReference;
import com.thenetworkplan.networkplan.refdata.domain.AircraftType;
import com.thenetworkplan.networkplan.refdata.repository.AircraftReferenceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings and Database.
 *
 * <p>The Database screen counts the reference sets for real, with one
 * statement per set over a fixed catalogue declared below. The table names
 * never come from the request — they are constants in this file — so the
 * dynamic SQL cannot be steered from outside.
 */
@Service
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    /**
     * The catalogue of reference sets: what they are, who owns them, and what
     * breaks when they are empty. Declared here because it describes the
     * product, not the tenant's data.
     */
    private record SetSpec(String code, String label, String schema, String table,
                           String ownedBy, String sourceOfTruth, String usedFor) {
    }

    private static final List<SetSpec> SETS = List.of(
            new SetSpec("AIRPORTS", "Aerodromes", "refdata", "airports", "DOM8 Reference Data",
                    "AIP", "Every station code on the board; the runway check of the readiness engine"),
            new SetSpec("RUNWAYS", "Runways", "refdata", "runways", "DOM8 Reference Data",
                    "AIP", "Published lengths, compared to the minimum runway of the aircraft type"),
            new SetSpec("AIRPORT_NOTES", "Aerodrome notes", "refdata", "airport_notes", "DOM8 Reference Data",
                    "Operator file", "PPR, curfews, slots and customs shown on Airports Data"),
            new SetSpec("AIRCRAFT_TYPES", "Aircraft types", "refdata", "aircraft_types", "DOM8 Reference Data",
                    "AFM / TCDS", "Performance and capacity; the runway and pax checks"),
            new SetSpec("DELAY_CODES", "IATA delay codes", "ops", "delay_codes", "DOM1 Flight Ops",
                    "IATA AHM 730", "Coding a delay; the delay report"),
            new SetSpec("MEL_LIBRARY", "Operator MEL", "camo", "mel_library", "MEL module",
                    "Operator MEL", "The rectification interval of every deferral"),
            new SetSpec("PROGRAMME_TASKS", "Maintenance programme", "camo", "programme_tasks", "CAMO Admin",
                    "AMP / MPD", "The intervals that produce every maintenance due date"),
            new SetSpec("DIRECTIVES", "Airworthiness directives", "camo", "directives", "CAMO Admin",
                    "EASA / FAA / manufacturer", "Compliance per registration"),
            new SetSpec("TRAINING_COURSES", "Training catalogue", "crew", "training_courses", "Training module",
                    "Training manual", "The validity of every certificate in a crew file"),
            new SetSpec("RISK_MATRIX", "Risk matrix 5x5", "safety", "risk_matrix", "Safety module",
                    "Safety management manual", "The level of every risk assessment"),
            new SetSpec("SUPPLIERS", "Suppliers", "tripsupport", "suppliers", "DOM2 Trip Support",
                    "Supplier contracts", "Who a service request goes to, and the notice they need"),
            new SetSpec("SETTINGS", "Operator settings", "platform", "settings", "Platform",
                    "Operations manual", "Thresholds read by the services named on each key"));

    private final SettingRepository settingRepository;
    private final AircraftRepository aircraftRepository;
    private final AircraftReferenceRepository referenceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminServiceImpl(SettingRepository settingRepository,
                            AircraftRepository aircraftRepository,
                            AircraftReferenceRepository referenceRepository) {
        this.settingRepository = settingRepository;
        this.aircraftRepository = aircraftRepository;
        this.referenceRepository = referenceRepository;
    }

    @Override
    public List<SettingDto> findSettings(UUID tenantId, String category) {
        String filter = (category == null || category.isBlank()) ? null : category.trim().toUpperCase();
        return settingRepository.findByTenantIdOrderByCategoryAscSettingKeyAsc(tenantId).stream()
                .filter(setting -> filter == null || setting.getCategory().equals(filter))
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public SettingDto updateSetting(UUID tenantId, UUID settingId, UpdateSettingCommand command, UUID actorId) {
        Setting setting = settingRepository.findByTenantIdAndId(tenantId, settingId)
                .orElseThrow(() -> ResourceNotFoundException.of("Setting", settingId));
        if (!setting.isEditable()) {
            throw new BusinessRuleException("SETTING_NOT_EDITABLE",
                    setting.getSettingKey() + " is derived or regulatory and is not edited here");
        }
        // The declared type is enforced: a threshold in minutes cannot become
        // the word "soon" because a form allowed it.
        validate(setting, command.settingValue().trim());

        setting.setSettingValue(command.settingValue().trim());
        setting.getSource().setAuthor(actorId);
        setting.getSource().setType("manual");
        setting.getSource().setAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toDto(settingRepository.save(setting));
    }

    /**
     * The whole Settings screen in one read.
     *
     * <p>One statement, grouped in memory: thirteen rubrics would otherwise be
     * thirteen round trips for a screen that shows one of them at a time, and
     * the left-hand rubric list needs every count anyway.
     *
     * <p>Rubrics with no rows still appear when they carry a screen of their
     * own — OPS qualifications and Administration have no fields by design.
     * A rubric that is empty for any other reason is left out rather than
     * shown as a dead entry.
     */
    @Override
    public SettingsFormDto findSettingsForm(UUID tenantId) {
        List<Setting> all = settingRepository.findByTenantIdOrderByCategoryAscSettingKeyAsc(tenantId);

        Map<String, List<Setting>> bySection = all.stream()
                .collect(Collectors.groupingBy(Setting::getSection));

        List<SettingsSectionDto> sections = new ArrayList<>();
        for (SettingsSection section : SettingsSection.values()) {
            List<Setting> rows = bySection.getOrDefault(section.name(), List.of());
            if (rows.isEmpty() && section.custom() == null) {
                continue;
            }

            // LinkedHashMap: the groups appear in the order the sort keys put
            // their first field, which is the order the prototype declares them.
            Map<String, List<SettingDto>> groups = new LinkedHashMap<>();
            rows.stream()
                    .sorted(Comparator.comparingInt(Setting::getSortOrder)
                            .thenComparing(Setting::getSettingKey))
                    .forEach(setting -> groups
                            .computeIfAbsent(setting.getGroupTitle(), key -> new ArrayList<>())
                            .add(toDto(setting)));

            int unread = (int) rows.stream().filter(setting -> setting.getReadBy() == null).count();

            sections.add(new SettingsSectionDto(
                    section.id(), section.name(), section.title(), section.blurb(),
                    section.hasActions(), section.custom(),
                    rows.size(), unread,
                    groups.entrySet().stream()
                            .map(entry -> new SettingsGroupDto(entry.getKey(), entry.getValue()))
                            .toList()));
        }

        int withReader = (int) all.stream().filter(setting -> setting.getReadBy() != null).count();
        return new SettingsFormDto(sections, all.size(), withReader);
    }

    /**
     * Puts a rubric, or the whole configuration, back to the seeded values.
     *
     * <p>Restores rather than deletes: the rows carry their own default, so a
     * reset is a write like any other and leaves the audit columns telling the
     * truth about who did it and when. The prototype deletes its stored keys
     * and falls back to the defaults in its source — which works only because
     * the defaults are in the browser.
     *
     * @param sectionName null to reset everything
     * @return how many rows actually moved
     */
    @Override
    @Transactional
    public int resetSettings(UUID tenantId, String sectionName, UUID actorId) {
        List<Setting> rows = settingRepository.findByTenantIdOrderByCategoryAscSettingKeyAsc(tenantId).stream()
                .filter(setting -> sectionName == null || setting.getSection().equals(sectionName))
                .filter(Setting::isEditable)
                .filter(setting -> !setting.getSettingValue().equals(setting.getDefaultValue()))
                .toList();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (Setting setting : rows) {
            setting.setSettingValue(setting.getDefaultValue());
            setting.getSource().setAuthor(actorId);
            setting.getSource().setType("manual");
            setting.getSource().setAt(now);
        }
        settingRepository.saveAll(rows);
        return rows.size();
    }

    /**
     * Restores a configuration file.
     *
     * <p>Every value is validated exactly as a single edit is, and an unknown
     * key is ignored rather than created: a configuration file from a newer
     * build must not be able to invent settings nothing reads. A rejected value
     * fails the whole import — half a restored configuration is worse than none,
     * because nobody can tell which half.
     *
     * @return how many rows were changed
     */
    @Override
    @Transactional
    public int importSettings(UUID tenantId, Map<String, String> values, UUID actorId) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        Map<String, Setting> byKey = settingRepository
                .findByTenantIdOrderByCategoryAscSettingKeyAsc(tenantId).stream()
                .collect(Collectors.toMap(Setting::getSettingKey, setting -> setting));

        List<Setting> changed = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            Setting setting = byKey.get(entry.getKey());
            if (setting == null || !setting.isEditable()) {
                continue;
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.equals(setting.getSettingValue())) {
                continue;
            }
            validate(setting, value);
            setting.setSettingValue(value);
            setting.getSource().setAuthor(actorId);
            setting.getSource().setType("import");
            setting.getSource().setAt(now);
            changed.add(setting);
        }
        settingRepository.saveAll(changed);
        return changed.size();
    }

    /**
     * The Fleet Register: every tail the operator holds, grouped by family.
     *
     * <p>Two statements — the fleet with its type and reference loaded, and
     * nothing else. The figures are the type's, not the tail's: a runway length
     * belongs to the aeroplane design, and storing it per registration is how
     * two aircraft of one type end up disagreeing about it.
     *
     * <p>Families come from the same rule the roster and crew scheduling use.
     * A third definition of "Falcon" would eventually contradict the other two.
     */
    @Override
    public FleetRegisterDto findFleetRegister(UUID tenantId) {
        List<Aircraft> fleet = aircraftRepository.findFleet(tenantId);

        Map<String, List<FleetRegisterRowDto>> byFamily = new LinkedHashMap<>();
        int serviceable = 0;
        int outOfService = 0;

        for (Aircraft aircraft : fleet) {
            AircraftType type = aircraft.getAircraftType();
            AircraftReference reference = type == null ? null : type.getReference();

            if ("SERVICEABLE".equals(aircraft.getStatus().name())) {
                serviceable++;
            } else {
                outOfService++;
            }

            String family = TypeFamily.of(type);
            byFamily.computeIfAbsent(family == null ? "OTHER" : family, key -> new ArrayList<>())
                    .add(new FleetRegisterRowDto(
                            aircraft.getId(),
                            aircraft.getRegistration(),
                            type == null ? null : type.getIcaoType(),
                            type == null ? null : type.getModel(),
                            reference == null ? null : reference.getModel(),
                            type == null ? null : type.getEngines(),
                            type == null ? null : type.getMtowKg(),
                            reference == null ? null : reference.maxPersons(),
                            reference == null ? null : reference.getSeats(),
                            type == null ? null : type.getMaxPax(),
                            type == null ? null : type.getRangeNm(),
                            type == null ? null : type.getCruiseTasKt(),
                            type == null ? null : type.getMinRunwayM(),
                            crewConfig(type),
                            type == null ? null : type.getCrewCertification(),
                            aircraft.getStatus().name(),
                            aircraft.getStatusReason()));
        }

        List<FleetFamilyDto> families = byFamily.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, List<FleetRegisterRowDto>> entry) ->
                                familyOrder(entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> new FleetFamilyDto(entry.getKey(), familyLabel(entry.getKey()),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(FleetRegisterRowDto::registration))
                                .toList()))
                .toList();

        return new FleetRegisterDto(families, fleet.size(), serviceable, outOfService);
    }

    /** "2/1" — flight deck over cabin. A dash when the type says nothing. */
    private String crewConfig(AircraftType type) {
        if (type == null || type.getCrewFlightDeck() == null || type.getCrewCabin() == null) {
            return null;
        }
        return type.getCrewFlightDeck() + "/" + type.getCrewCabin();
    }

    /**
     * The order the families appear in, which is the prototype's.
     *
     * <p>Heaviest and longest-range first, down to the light twins — the order
     * a sales desk reads a fleet in when it is looking for something that can
     * do the trip. Alphabetical would put Citation at the top, which is the one
     * family that can do the fewest of them.
     *
     * <p>Anything unrecognised sorts last rather than first: a type nobody has
     * classified is not the headline of the register.
     */
    private int familyOrder(String family) {
        return switch (family) {
            case "FALCON" -> 0;
            case "CITATION" -> 1;
            case "LEGACY" -> 2;
            case "LINEAGE" -> 3;
            default -> 9;
        };
    }

    private String familyLabel(String family) {
        return switch (family) {
            case "LINEAGE" -> "Embraer Lineage 1000";
            case "OTHER" -> "Unclassified";
            default -> family.charAt(0) + family.substring(1).toLowerCase();
        };
    }

    /**
     * The aircraft reference, searched.
     *
     * @param search matched against designator, manufacturer and model; null returns the head of the list
     */
    @Override
    public List<AircraftReferenceDto> findAircraftReference(String search, int limit) {
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase() + "%";
        return referenceRepository
                .search(pattern, org.springframework.data.domain.PageRequest.of(0, Math.max(1, Math.min(limit, 500))))
                .stream()
                .map(this::toDto)
                .toList();
    }

    private AircraftReferenceDto toDto(AircraftReference reference) {
        return new AircraftReferenceDto(
                reference.getId(), reference.getManufacturer(), reference.getModel(),
                reference.getIcaoType(), reference.getDescriptor(),
                reference.getWakeCategory(), reference.getWakeSource(),
                reference.getMtowKg(), reference.getMzfwKg(), reference.getMlwKg(),
                reference.getDowKg(), reference.getFuelKg(), reference.getFuelL(),
                reference.getSeats(), reference.getPob(), reference.maxPersons(),
                reference.getRangeNm(), reference.getRangeMaxPayloadNm(),
                reference.getRangeFullPaxNm(), reference.getRangeMaxFuelNm(),
                reference.getMach(), reference.getCruiseTasKt(), reference.getOptimumFl(),
                reference.getTakeoffDistanceM(), reference.getRffsCategory(),
                reference.getEtopsMinutes() == null ? List.of() : List.of(reference.getEtopsMinutes()));
    }

    @Override
    public DatabaseBoardDto findDatabaseBoard(UUID tenantId) {
        List<ReferenceSetDto> rows = new ArrayList<>(SETS.size());
        long total = 0;
        int empty = 0;

        for (SetSpec spec : SETS) {
            // The table name is a constant from SETS, never a parameter of the
            // request: nothing here can be steered from outside.
            Object[] answer = (Object[]) entityManager
                    .createNativeQuery("select count(*), max(updated_at) from "
                            + spec.schema() + "." + spec.table())
                    .getSingleResult();

            long count = ((Number) answer[0]).longValue();
            // The driver hands a timestamptz back as an Instant on a native
            // query; the typed path of JPA is what turns it into an offset.
            OffsetDateTime updatedAt = switch (answer[1]) {
                case null -> null;
                case OffsetDateTime offset -> offset;
                case java.time.Instant instant -> instant.atOffset(ZoneOffset.UTC);
                case java.sql.Timestamp timestamp -> timestamp.toInstant().atOffset(ZoneOffset.UTC);
                default -> null;
            };

            total += count;
            if (count == 0) {
                empty++;
            }
            rows.add(new ReferenceSetDto(
                    spec.code(), spec.label(), spec.schema(), spec.table(),
                    spec.ownedBy(), count, updatedAt, spec.sourceOfTruth(), spec.usedFor()));
        }

        return new DatabaseBoardDto(rows, total, empty, OffsetDateTime.now(ZoneOffset.UTC));
    }

    // ----------------------------------------------------------------

    private void validate(Setting setting, String value) {
        try {
            switch (setting.getValueType()) {
                case "INTEGER" -> checkBounds(setting, new BigDecimal(Integer.parseInt(value)));
                case "DECIMAL" -> checkBounds(setting, new BigDecimal(value));
                case "BOOLEAN" -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new NumberFormatException(value);
                    }
                }
                case "DURATION" -> java.time.Duration.parse(value);
                // A list can legitimately become empty: removing the last default
                // service is a decision, not a typo.
                case "LIST" -> { }
                case "ENUM" -> checkOption(setting, value);
                default -> {
                    if (value.isBlank()) {
                        throw new NumberFormatException("empty");
                    }
                }
            }
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("SETTING_VALUE_INVALID",
                    setting.getSettingKey() + " is declared as " + setting.getValueType()
                            + "; \"" + value + "\" is not one");
        }
    }

    /**
     * The bounds the form advertises are enforced here too.
     *
     * <p>A browser honours {@code min} and {@code max} on a number field; a
     * curl command does not, and the service that reads the key would get a
     * refresh interval of minus four seconds.
     */
    private void checkBounds(Setting setting, BigDecimal value) {
        if (setting.getMinValue() != null && value.compareTo(setting.getMinValue()) < 0) {
            throw new BusinessRuleException("SETTING_VALUE_OUT_OF_RANGE",
                    setting.getSettingKey() + " cannot be below " + setting.getMinValue().stripTrailingZeros().toPlainString());
        }
        if (setting.getMaxValue() != null && value.compareTo(setting.getMaxValue()) > 0) {
            throw new BusinessRuleException("SETTING_VALUE_OUT_OF_RANGE",
                    setting.getSettingKey() + " cannot be above " + setting.getMaxValue().stripTrailingZeros().toPlainString());
        }
    }

    /** An enumerated setting takes one of the values its own control offers, and no other. */
    private void checkOption(Setting setting, String value) {
        List<Setting.Option> options = setting.getOptions();
        if (options == null || options.isEmpty()) {
            return;
        }
        boolean known = options.stream().anyMatch(option -> option.value().equals(value));
        if (!known) {
            throw new BusinessRuleException("SETTING_VALUE_INVALID",
                    setting.getSettingKey() + " accepts "
                            + options.stream().map(Setting.Option::value).collect(Collectors.joining(", "))
                            + "; \"" + value + "\" is none of them");
        }
    }

    private SettingDto toDto(Setting setting) {
        return new SettingDto(
                setting.getId(), setting.getCategory(), setting.getSettingKey(),
                setting.getSettingValue(), setting.getDefaultValue(), setting.getValueType(),
                setting.getUnit(), setting.getLabel(), setting.getDescription(), setting.getReadBy(),
                setting.isEditable(),
                setting.getControl(),
                setting.getOptions() == null ? null : setting.getOptions().stream()
                        .map(option -> new SettingOptionDto(option.value(), option.label()))
                        .toList(),
                setting.getPlaceholder(),
                setting.getMinValue(), setting.getMaxValue(), setting.getStepValue(),
                setting.getShowIfKey(), setting.getShowIfValue(),
                setting.getUpdatedAt());
    }
}
