package com.thenetworkplan.networkplan.vigil.domain;

/**
 * Le cycle de vie d'une alerte VIGIL — OPEN, ACKNOWLEDGED, IN PROGRESS,
 * RESOLVED ou DISMISSED.
 *
 * <p>Les trois premiers sont « actifs » : l'alerte compte dans les
 * compteurs et reste dans la liste. Une alerte resolue par le balayage
 * est ROUVERTE si la condition revient — regle de l'annexe (l. 98700).
 */
public enum VigilAlertStatus {
    OPEN,
    ACKNOWLEDGED,
    IN_PROGRESS,
    RESOLVED,
    DISMISSED;

    public boolean active() {
        return this == OPEN || this == ACKNOWLEDGED || this == IN_PROGRESS;
    }
}
