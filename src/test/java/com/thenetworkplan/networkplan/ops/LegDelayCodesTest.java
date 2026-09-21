package com.thenetworkplan.networkplan.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.airworthiness.repository.MelItemRepository;
import com.thenetworkplan.networkplan.config.OpsProperties;
import com.thenetworkplan.networkplan.ops.domain.DelayCode;
import com.thenetworkplan.networkplan.ops.dto.DelayCodeDto;
import com.thenetworkplan.networkplan.ops.mapper.LegMapper;
import com.thenetworkplan.networkplan.ops.repository.DelayCodeRepository;
import com.thenetworkplan.networkplan.ops.repository.DelayRecordRepository;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import com.thenetworkplan.networkplan.ops.service.LegEventRecorder;
import com.thenetworkplan.networkplan.ops.service.impl.LegServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La liste des codes de retard, exposee au dossier de vol.
 *
 * <p>Le serveur enregistre un retard code avec le mouvement OUT depuis
 * toujours (RecordMovementCommand.delayMinutes / delayCode), mais aucun ecran
 * ne le demandait : la cause tombait sur « 89 » par defaut, en silence. Pour
 * que l'OCC choisisse, il faut d'abord qu'il voie la liste du tenant.
 */
class LegDelayCodesTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final DelayCodeRepository delayCodeRepository = mock(DelayCodeRepository.class);

    private final LegServiceImpl service = new LegServiceImpl(
            mock(LegRepository.class), mock(AircraftRepository.class), mock(MelItemRepository.class),
            mock(DelayRecordRepository.class), delayCodeRepository, mock(LegEventRecorder.class),
            mock(LegMapper.class), new OpsProperties());

    @Test
    @DisplayName("delayCodes rend les codes actifs du tenant, dans l'ordre des codes, avec leur libelle")
    void delayCodesOfTheTenant() {
        when(delayCodeRepository.findByTenantIdAndActiveTrueOrderByCodeAsc(TENANT))
                .thenReturn(List.of(code("71", "Departure station weather"),
                        code("93", "Aircraft rotation, late arrival of another leg")));

        List<DelayCodeDto> codes = service.delayCodes(TENANT);

        assertThat(codes).extracting(DelayCodeDto::code, DelayCodeDto::label)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("71", "Departure station weather"),
                        org.assertj.core.groups.Tuple.tuple("93", "Aircraft rotation, late arrival of another leg"));
    }

    private static DelayCode code(String code, String label) {
        DelayCode entity = new DelayCode();
        entity.setTenantId(TENANT);
        entity.setCode(code);
        entity.setLabel(label);
        return entity;
    }
}
