package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record CountryStatusDto(
        String countryIso2,
        String status,
        String instrumentRef,
        Integer leadTimeHours,
        OffsetDateTime deadlineAt,
        String asaCorpusVersion) implements Serializable {
}
