package com.thenetworkplan.networkplan.ops.dto;

import jakarta.validation.constraints.Size;

/**
 * La note d'exploitation d'une etape.
 *
 * <p>Une note vide efface la note : c'est le geste qu'attend quiconque a saisi
 * une consigne devenue caduque, et l'annexe ne l'offrait pas.
 */
public record SaveFlightNoteCommand(
        @Size(max = 4000) String note) {
}
