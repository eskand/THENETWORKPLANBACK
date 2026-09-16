package com.thenetworkplan.networkplan.refdata.service;

import com.thenetworkplan.networkplan.refdata.dto.AircraftTypeDto;

public interface AircraftTypeService {

    AircraftTypeDto findByIcaoType(String icaoType);
}
