package com.thenetworkplan.networkplan.ops.dto;

import java.io.Serializable;

/** Un code de retard de la liste du tenant (IATA par defaut), pour le choix a la saisie de l'ATD. */
public record DelayCodeDto(String code, String label) implements Serializable {
}
