package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * L'onglet FUEL d'une etape : le fournisseur retenu au depart et son tarif.
 *
 * <p>Le fournisseur n'est pas invente : c'est celui de la demande de carburant
 * de l'escale de depart (onglet SERVICES). Les deux onglets ne peuvent donc pas
 * se contredire, ce qui etait possible dans l'annexe — son onglet FUEL tirait le
 * fournisseur d'un tirage aleatoire quand aucun tarif n'etait importe.
 *
 * <p>{@code price} est nul quand aucun tarif n'est en vigueur a cette escale. Le
 * dossier l'ecrit alors « NO DATA » : un prix invente est une facture fausse.
 */
public record LegFuelDto(
        UUID legId,
        String stationIcao,
        String supplierName,
        String fuelGrade,
        BigDecimal price,
        String unit,
        String currency,
        String fees,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String requestStatus,
        boolean released) implements Serializable {
}
