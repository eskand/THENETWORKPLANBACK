package com.thenetworkplan.networkplan.tripsupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermitRequestCommand(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$", message = "must be an ISO 3166-1 alpha-2 country code")
        String countryIso2,
        @NotBlank String kind,
        @Size(max = 200) String recipient) {
}
