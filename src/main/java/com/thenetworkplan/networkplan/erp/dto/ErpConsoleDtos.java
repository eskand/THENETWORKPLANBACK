package com.thenetworkplan.networkplan.erp.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The read models of the crisis console.
 *
 * <p>Separate from {@link ErpDtos} on purpose: {@code ErpDtos} answers "what is
 * the plan", these answer "what is happening". The first is read by an auditor,
 * the second by someone under pressure, and they are rarely wanted together.
 */
public final class ErpConsoleDtos {

    private ErpConsoleDtos() {
    }

    /** One of the five levels, as the operator wrote it. */
    public record LevelDto(
            short level,
            String name,
            String colour,
            String description,
            String activation,
            String standsUp) implements Serializable {
    }

    /** One cell of the crisis organisation, with the person who actually holds it. */
    public record CellDto(
            String code,
            String name,
            String scope,
            String colour,
            String leadRole,
            /** The name from the personnel register, or null when nobody holds the post. */
            String leadName,
            int callOrder,
            /** Items in force at the current level, and how many are done. */
            int itemsTotal,
            int itemsDone,
            int percent) implements Serializable {
    }

    /** One line of the readiness list on the armed console. */
    public record ReadinessDto(
            String name,
            String detail,
            boolean ok) implements Serializable {
    }

    public record EventCategoryDto(
            String code,
            String name,
            List<EventDto> events) implements Serializable {
    }

    public record EventDto(
            String code,
            String categoryCode,
            short baseLevel,
            String squawk,
            String label,
            String note) implements Serializable {
    }

    public record QuestionDto(
            String code,
            String question,
            List<QuestionOptionDto> options) implements Serializable {
    }

    public record QuestionOptionDto(
            String value,
            String label,
            short level) implements Serializable {
    }

    /** The catalogue and the questionnaire — reference, and the same for every operator. */
    public record CatalogueDto(
            List<EventCategoryDto> categories,
            List<QuestionDto> questions) implements Serializable {
    }

    /** One reason the assessment landed where it did. */
    public record DriverDto(
            String from,
            String text,
            short level) implements Serializable {
    }

    /**
     * The verdict of the severity assessment.
     *
     * <p>{@code escalated} says the questions pushed the level above the event's
     * own base. The reverse cannot happen: the assessment never lowers a level.
     */
    public record AssessmentDto(
            short level,
            short baseLevel,
            LevelDto definition,
            EventDto event,
            List<DriverDto> drivers,
            int answered,
            int total,
            boolean complete,
            boolean escalated) implements Serializable {
    }

    /** The aircraft the crisis is about. */
    public record SubjectDto(
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
            String souls) implements Serializable {
    }

    public record ChecklistItemDto(
            String code,
            String deptCode,
            Short minLevel,
            String text,
            boolean done,
            OffsetDateTime doneAt,
            String doneBy) implements Serializable {
    }

    public record ChecklistDto(
            String deptCode,
            String deptName,
            String colour,
            String leadName,
            List<ChecklistItemDto> items,
            int total,
            int done,
            int percent) implements Serializable {
    }

    public record NotificationDto(
            String code,
            short minLevel,
            String target,
            String withinLabel,
            String basis,
            String note,
            /** Whether it applies at the level the crisis is running at. */
            boolean required,
            boolean made,
            OffsetDateTime madeAt,
            String madeBy,
            String channel,
            String reference) implements Serializable {
    }

    /** A template with its tokens already filled from the live event. */
    public record TemplateDto(
            String code,
            short minLevel,
            String audience,
            String title,
            String body,
            boolean available,
            /** Tokens with nothing to fill them. The screen marks them; it does not hide them. */
            List<String> unresolved) implements Serializable {
    }

    public record SitrepDto(
            UUID id,
            OffsetDateTime at,
            short level,
            String body,
            String author) implements Serializable {
    }

    public record LogEntryDto(
            UUID id,
            OffsetDateTime at,
            String kind,
            String text,
            String actor,
            Short level) implements Serializable {
    }

    public record StandDownCriterionDto(
            String code,
            String text) implements Serializable {
    }

    /** One thing that should be done next, and where to go to do it. */
    public record PriorityDto(
            String colour,
            String text,
            String target) implements Serializable {
    }

    /** The live event, or null when the plan is merely armed. */
    public record ActiveEventDto(
            UUID id,
            String reference,
            String kind,
            short level,
            LevelDto definition,
            String eventLabel,
            String eventCode,
            OffsetDateTime activatedAt,
            long elapsedMinutes,
            String initiatedByName,
            String initiatedByRole,
            String concurredByName,
            String concurredByRole,
            String overrideReason,
            SubjectDto subject,
            boolean commsIssued,
            List<CellDto> cells,
            ChecklistDto phaseZero,
            List<PriorityDto> priorities,
            List<NotificationDto> notifications,
            List<SitrepDto> sitreps,
            int outstanding) implements Serializable {
    }

    /**
     * Everything the console shows on opening.
     *
     * <p>One call rather than eight: a console assembled from eight requests
     * shows eight different moments of the same crisis.
     */
    public record ConsoleDto(
            boolean armed,
            ActiveEventDto active,
            String planCode,
            String planTitle,
            String revision,
            String operator,
            String aocReference,
            List<LevelDto> levels,
            List<CellDto> cells,
            List<ReadinessDto> readiness,
            List<ErpDtos.ErpActivationDto> history,
            List<ErpDtos.ErpExerciseDto> exercises,
            List<LogEntryDto> log,
            OffsetDateTime computedAt) implements Serializable {
    }

    /** The Reference tab: the plan itself, with no event in progress. */
    public record ReferenceDto(
            List<LevelDto> levels,
            List<NotificationDto> notifications,
            List<TemplateDto> templates,
            List<StandDownCriterionDto> standDown,
            List<CellDto> cells,
            List<ChecklistDto> checklists,
            String crisisPhone,
            String mediaEmail,
            String ercLocation) implements Serializable {
    }
}
