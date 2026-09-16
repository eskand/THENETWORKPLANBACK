package com.thenetworkplan.networkplan.refdata.dto;

import java.io.Serializable;
import java.util.UUID;

public record RunwayDto(
        UUID id,
        String designator,
        int lengthFt,
        Integer widthFt,
        String surface,
        Integer ldaFt,
        Integer todaFt,
        String ilsCategory,
        String lighting) implements Serializable {
}
