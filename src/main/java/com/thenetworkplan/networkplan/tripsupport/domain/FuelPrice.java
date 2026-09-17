package com.thenetworkplan.networkplan.tripsupport.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

/**
 * Le tarif carburant d'une escale chez un fournisseur.
 *
 * <p>L'unite est stockee telle que le fournisseur la cote — au gallon ou au
 * litre — et jamais convertie avant enregistrement : convertir a l'ecriture
 * ferait perdre la valeur contractuelle et introduirait un arrondi que personne
 * ne pourrait plus rapprocher de la facture.
 */
@Entity
@Table(name = "fuel_prices", schema = "tripsupport")
@Getter
@Setter
public class FuelPrice extends BaseEntity {

    @Column(name = "station_icao", nullable = false)
    private String stationIcao;

    @Column(name = "supplier_name", nullable = false)
    private String supplierName;

    @Column(name = "fuel_grade", nullable = false)
    private String fuelGrade = "JET A-1";

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false)
    private FuelPriceUnit unit = FuelPriceUnit.USG;

    @Column(name = "currency", nullable = false)
    private String currency = "USD";

    @Column(name = "fees")
    private String fees;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;
}
