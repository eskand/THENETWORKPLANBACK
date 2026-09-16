package com.thenetworkplan.networkplan.common.tenant;

import com.thenetworkplan.networkplan.config.TenantProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/** Binds the tenant to the request thread, and unbinds it whatever happens. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(TenantFilter.class);

    private final TenantProperties properties;

    public TenantFilter(TenantProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            TenantContext.set(resolve(request));
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private UUID resolve(HttpServletRequest request) {
        String raw = request.getHeader(properties.getHeader());
        if (!StringUtils.hasText(raw)) {
            return properties.getDefaultId();
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            LOG.warn("Ignoring malformed {} header: {}", properties.getHeader(), raw);
            return properties.getDefaultId();
        }
    }
}
