package com.thenetworkplan.networkplan.common.tenant;

import com.thenetworkplan.networkplan.common.exception.BusinessRuleException;
import java.util.UUID;

/**
 * Tenant of the request being served, held for the duration of that request.
 *
 * <p>Authentication is not wired yet: {@link TenantFilter} takes the tenant from
 * a header and falls back to the configured demo tenant. When OIDC arrives, only
 * the filter changes — every query already goes through this class.
 *
 * <p>A {@link ThreadLocal} is correct on virtual threads (one carrier-independent
 * value per virtual thread), but it does <em>not</em> follow work handed to
 * another thread: code that fans out must read the tenant first and pass it as a
 * parameter. See {@code DispatchBoardServiceImpl}.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT.set(tenantId);
    }

    public static UUID require() {
        UUID tenantId = CURRENT.get();
        if (tenantId == null) {
            throw new BusinessRuleException("TENANT_MISSING", "No tenant bound to the current request");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
