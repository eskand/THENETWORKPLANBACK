package com.thenetworkplan.networkplan.tripsupport.mapper;

import com.thenetworkplan.networkplan.tripsupport.domain.CountryStatus;
import com.thenetworkplan.networkplan.tripsupport.domain.PermitRequest;
import com.thenetworkplan.networkplan.tripsupport.domain.ServiceRequest;
import com.thenetworkplan.networkplan.tripsupport.dto.CountryStatusDto;
import com.thenetworkplan.networkplan.tripsupport.dto.PermitRequestDto;
import com.thenetworkplan.networkplan.tripsupport.dto.ServiceRequestDto;
import org.springframework.stereotype.Component;

@Component
public class TripSupportMapper {

    public ServiceRequestDto toDto(ServiceRequest entity) {
        return new ServiceRequestDto(
                entity.getId(),
                entity.getLegId(),
                entity.getStationIcao(),
                entity.getServiceType().name(),
                entity.getSupplierName(),
                entity.getStatus().name(),
                entity.getReference(),
                entity.getSentAt(),
                entity.getAcknowledgedAt(),
                entity.getConfirmedAt(),
                entity.getRemark());
    }

    public PermitRequestDto toDto(PermitRequest entity) {
        return new PermitRequestDto(
                entity.getId(),
                entity.getLegId(),
                entity.getCountryIso2(),
                entity.getKind().name(),
                entity.getStatus().name(),
                entity.getRecipient(),
                entity.getReference(),
                entity.getSentAt(),
                entity.getConfirmedAt(),
                entity.getValidFrom(),
                entity.getValidTo());
    }

    public CountryStatusDto toDto(CountryStatus entity) {
        return new CountryStatusDto(
                entity.getCountryIso2(),
                entity.getStatus().name(),
                entity.getInstrumentRef(),
                entity.getLeadTimeHours(),
                entity.getDeadlineAt(),
                entity.getAsaCorpusVersion());
    }
}
