package com.thenetworkplan.networkplan.tripsupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateServiceRequestCommand(
        @NotBlank @Pattern(regexp = "^[A-Z]{4}$", message = "must be a four-letter ICAO code")
        String stationIcao,
        @NotBlank String serviceType,
        @Size(max = 200) String supplierName,
        @Size(max = 500) String remark) {
}
