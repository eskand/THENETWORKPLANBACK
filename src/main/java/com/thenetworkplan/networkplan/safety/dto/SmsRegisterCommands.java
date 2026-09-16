package com.thenetworkplan.networkplan.safety.dto;

import jakarta.validation.constraints.NotBlank;

/** What the hazard register and the reporting desk can change. */
public final class SmsRegisterCommands {

    private SmsRegisterCommands() {
    }

    /**
     * The Safety Manager asks the reporter for more.
     *
     * <p>Named, because a question arriving at a reporter from nobody is a
     * question they are entitled to ignore.
     */
    public record AskQueryCommand(
            @NotBlank String question,
            @NotBlank String askedBy) {
    }

    public record AnswerQueryCommand(
            @NotBlank String answer) {
    }
}
