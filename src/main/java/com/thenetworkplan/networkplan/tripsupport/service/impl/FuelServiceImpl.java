package com.thenetworkplan.networkplan.tripsupport.service.impl;

import com.thenetworkplan.networkplan.common.exception.ResourceNotFoundException;
import com.thenetworkplan.networkplan.ops.domain.Leg;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.tripsupport.domain.FuelPrice;
import com.thenetworkplan.networkplan.tripsupport.domain.GroundServiceType;
import com.thenetworkplan.networkplan.tripsupport.domain.RequestStatus;
import com.thenetworkplan.networkplan.tripsupport.domain.ServiceRequest;
import com.thenetworkplan.networkplan.tripsupport.dto.LegFuelDto;
import com.thenetworkplan.networkplan.tripsupport.repository.FuelPriceRepository;
import com.thenetworkplan.networkplan.tripsupport.repository.ServiceRequestRepository;
import com.thenetworkplan.networkplan.tripsupport.service.FuelService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le carburant d'une etape.
 *
 * <p><b>Le fournisseur vient de l'onglet SERVICES</b>, pas d'une source a lui :
 * c'est la demande de carburant de l'escale de depart. Les deux onglets du meme
 * dossier ne peuvent donc pas nommer deux fournisseurs differents — ce qui
 * arrivait dans l'annexe, ou l'onglet FUEL tirait un nom au hasard quand aucun
 * tarif n'avait ete importe (l. 14304).
 *
 * <p><b>Le tarif est celui en vigueur le jour du vol</b>, chez ce fournisseur si
 * la liste le couvre, sinon le premier tarif en vigueur a l'escale. Quand rien
 * ne couvre l'escale, le prix est nul et le dossier ecrit « NO DATA » : un prix
 * invente devient une facture fausse.
 */
@Service
@Transactional(readOnly = true)
public class FuelServiceImpl implements FuelService {

    private final LegRepository legRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final FuelPriceRepository fuelPriceRepository;

    public FuelServiceImpl(LegRepository legRepository,
                           ServiceRequestRepository serviceRequestRepository,
                           FuelPriceRepository fuelPriceRepository) {
        this.legRepository = legRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.fuelPriceRepository = fuelPriceRepository;
    }

    @Override
    public LegFuelDto findByLeg(UUID tenantId, UUID legId) {
        Leg leg = legRepository.findOneWithDetails(tenantId, legId)
                .orElseThrow(() -> ResourceNotFoundException.of("Leg", legId));
        String station = leg.getDepIcao();

        Optional<ServiceRequest> fuelRequest = serviceRequestRepository
                .findByTenantIdAndLegIdAndStationIcaoAndServiceType(
                        tenantId, legId, station, GroundServiceType.FUEL);
        String supplier = fuelRequest.map(ServiceRequest::getSupplierName).orElse(null);
        String requestStatus = fuelRequest.map(request -> request.getStatus().name()).orElse(null);
        boolean released = fuelRequest
                .map(request -> request.getStatus() == RequestStatus.CONFIRMED)
                .orElse(false);

        LocalDate day = leg.effectiveDeparture().atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        List<FuelPrice> inForce = fuelPriceRepository.findInForce(tenantId, station, day);
        FuelPrice price = inForce.stream()
                .filter(row -> supplier != null && supplier.equalsIgnoreCase(row.getSupplierName()))
                .findFirst()
                .orElse(inForce.isEmpty() ? null : inForce.get(0));

        return new LegFuelDto(
                legId,
                station,
                supplier != null ? supplier : (price == null ? null : price.getSupplierName()),
                price == null ? null : price.getFuelGrade(),
                price == null ? null : price.getPrice(),
                price == null ? null : price.getUnit().name(),
                price == null ? null : price.getCurrency(),
                price == null ? null : price.getFees(),
                price == null ? null : price.getEffectiveFrom(),
                price == null ? null : price.getEffectiveTo(),
                requestStatus,
                released);
    }
}
