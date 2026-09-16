package com.thenetworkplan.networkplan.config;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "netplus.tenant")
@Getter
@Setter
public class TenantProperties {

    /** Tenant used when the request carries no tenant header. */
    private UUID defaultId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** Header carrying the tenant id until authentication is wired. */
    private String header = "X-Tenant-Id";
}
