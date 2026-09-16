package com.thenetworkplan.networkplan.weather.domain;

import com.thenetworkplan.networkplan.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One observation, as received.
 *
 * <p>{@code rawText} is kept whatever happens: a decoder is a piece of code
 * that can be wrong, and the message is the only thing that cannot. Every
 * decoded field is nullable — a group the METAR does not carry stays empty
 * rather than defaulting to zero.
 */
@Entity
@Table(name = "weather_observations", schema = "refdata")
@Getter
@Setter
public class WeatherObservation extends BaseEntity {

    @Column(name = "station_icao", nullable = false)
    private String stationIcao;

    @Column(name = "report_type", nullable = false)
    private String reportType = "METAR";

    /** The moment the observation was made, from the {@code ddhhmmZ} group. */
    @Column(name = "observed_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime observedAt;

    /** The moment we learnt it. The gap is the latency an OCC needs to judge. */
    @Column(name = "received_at", columnDefinition = "timestamptz", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "wind_dir_deg")
    private Integer windDirDeg;

    @Column(name = "wind_variable", nullable = false)
    private boolean windVariable;

    @Column(name = "wind_speed_kt")
    private Integer windSpeedKt;

    @Column(name = "wind_gust_kt")
    private Integer windGustKt;

    @Column(name = "visibility_m")
    private Integer visibilityM;

    @Column(name = "cavok", nullable = false)
    private boolean cavok;

    /** Lowest broken or overcast layer, in feet. Null when the sky is clear. */
    @Column(name = "ceiling_ft")
    private Integer ceilingFt;

    @Column(name = "temperature_c")
    private Integer temperatureC;

    @Column(name = "dewpoint_c")
    private Integer dewpointC;

    @Column(name = "qnh_hpa")
    private Integer qnhHpa;

    @Column(name = "conditions")
    private String conditions;

    @Column(name = "flight_category")
    private String flightCategory;
}
