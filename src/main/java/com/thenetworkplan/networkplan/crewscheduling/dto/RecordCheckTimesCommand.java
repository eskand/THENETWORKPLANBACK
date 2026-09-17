package com.thenetworkplan.networkplan.crewscheduling.dto;

import java.time.OffsetDateTime;

/**
 * Les heures reelles de prise et de fin de service d'un membre d'equipage.
 *
 * <p>Les deux champs sont facultatifs et nuls veut dire « efface » : corriger
 * une saisie fausse fait partie du geste, et une heure qu'on ne peut plus
 * retirer finit par etre contournee par une deuxieme ligne.
 *
 * <p>Le service est la source du calcul FDP — l'annexe le repete a chaque
 * ligne de son onglet CREW : le temps de service court de la prise de service a
 * la fin de service, jamais du bloc ni du STD.
 */
public record RecordCheckTimesCommand(
        OffsetDateTime checkInAt,
        OffsetDateTime checkOutAt) {
}
