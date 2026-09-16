package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;

/**
 * One service provider at an aerodrome, as the directory lists it.
 *
 * <p>Not a supplier under contract — that is {@code SupplierDto}. This is who
 * is there, whether the operator has ever used them or not.
 */
public record AirportServiceDto(
        String serviceType,
        String name,
        String phone,
        String afterHours,
        String fax,
        String email,
        String website,
        String hours,
        String fuelBrands,
        String frequency) implements Serializable {
}
