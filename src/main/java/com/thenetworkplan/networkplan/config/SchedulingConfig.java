package com.thenetworkplan.networkplan.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Les taches planifiees du serveur. La premiere est la photo a H-1 des
 * departs ({@code DepartureSnapshotScheduler}) ; avant elle, tout balayage
 * etait declenche par une lecture d'ecran.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
