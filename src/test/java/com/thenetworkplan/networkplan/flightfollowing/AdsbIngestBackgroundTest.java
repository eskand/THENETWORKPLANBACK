package com.thenetworkplan.networkplan.flightfollowing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.thenetworkplan.networkplan.airworthiness.repository.AircraftRepository;
import com.thenetworkplan.networkplan.config.AdsbProperties;
import com.thenetworkplan.networkplan.flightfollowing.repository.PositionReportRepository;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbIngestService;
import com.thenetworkplan.networkplan.flightfollowing.service.AdsbSource;
import com.thenetworkplan.networkplan.flightfollowing.service.impl.AdsbIngestScheduler;
import com.thenetworkplan.networkplan.flightfollowing.service.impl.AdsbIngestServiceImpl;
import com.thenetworkplan.networkplan.ops.repository.LegRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Le tableau Flight Following ne doit plus attendre OpenSky.
 *
 * <p>Audit du 23/09 : findBoard appelait ingest() dans la requete HTTP. Fenetre de
 * 30 s, relecture du front toutes les 30 s : presque chaque relecture payait un
 * appel au fournisseur (0,7 s mesure, jusqu'au timeout de 10 s), pour un tableau
 * qui se compose en 20 ms. La lecture rend desormais le dernier releve ; le
 * releve suivant est fait en tache de fond, pour les seuls tenants dont l'ecran
 * est ouvert — le quota n'est pas depense pour un ecran ferme.
 */
class AdsbIngestBackgroundTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private AdsbSource source;
    private AdsbIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        source = mock(AdsbSource.class);
        when(source.isEnabled()).thenReturn(true);
        when(source.provider()).thenReturn("OPENSKY");
        when(source.fetch()).thenReturn(List.of());
        AircraftRepository aircraft = mock(AircraftRepository.class);
        when(aircraft.findFleet(any())).thenReturn(List.of());
        AdsbProperties properties = new AdsbProperties();
        // fenetre nulle : sans la correction, chaque lecture referait l'appel
        properties.setRefreshAfter(Duration.ZERO);
        service = new AdsbIngestServiceImpl(aircraft, mock(LegRepository.class),
                mock(PositionReportRepository.class), List.of(source), properties);
    }

    @Test
    void theFirstReadIngestsSoTheFirstBoardHasPositions() {
        AdsbIngestService.Result result = service.current(TENANT);

        assertThat(result.state()).isEqualTo("NO_ANSWER");
        verify(source, times(1)).fetch();
    }

    @Test
    void laterReadsNeverWaitOnTheProvider() {
        service.current(TENANT);
        service.current(TENANT);
        service.current(TENANT);

        verify(source, times(1)).fetch();
    }

    @Test
    void theSchedulerIngestsOnlyTenantsWhoseScreenIsOpen() {
        UUID idle = UUID.randomUUID();
        service.current(TENANT);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(service.watchedSince(now.minusMinutes(2))).containsExactly(TENANT);
        assertThat(service.watchedSince(now.plusMinutes(1))).isEmpty();

        AdsbIngestService spy = mock(AdsbIngestService.class);
        when(spy.watchedSince(any())).thenReturn(List.of(TENANT));
        new AdsbIngestScheduler(spy).sweep();
        verify(spy, times(1)).ingest(TENANT);
        verify(spy, never()).ingest(idle);
    }
}
