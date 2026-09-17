package com.thenetworkplan.networkplan.tripsupport.service;

import com.thenetworkplan.networkplan.tripsupport.dto.LegFuelDto;
import java.util.UUID;

/** L'onglet FUEL d'une etape : le fournisseur retenu au depart et son tarif. */
public interface FuelService {

    LegFuelDto findByLeg(UUID tenantId, UUID legId);
}
