package com.thenetworkplan.networkplan.tripsupport.dto;

import java.io.Serializable;
import java.util.UUID;

public record SupplierDto(
        UUID id,
        String stationIcao,
        String serviceType,
        String name,
        String email,
        String phone,
        String sita,
        String contractRef,
        boolean preferred,
        Integer leadTimeHours,
        String remark) implements Serializable {
}
