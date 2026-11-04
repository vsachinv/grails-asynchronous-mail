package grails.plugin.asyncmail

import groovy.transform.CompileStatic

/**
 * Thrown when a VPD scope is requested for one tenant while a scope for a different tenant is already active on
 * the current thread. Nested scopes must agree on the tenant; switching inside an active scope is not supported.
 */
@CompileStatic
class VPDContextMismatchException extends IllegalStateException {

    final Long activeTenantId
    final Long requestedTenantId

    VPDContextMismatchException(Long activeTenantId, Long requestedTenantId) {
        super("VPD context mismatch: a scope for tenant ${activeTenantId} is active on this thread " +
                "but tenant ${requestedTenantId} was requested".toString())
        this.activeTenantId = activeTenantId
        this.requestedTenantId = requestedTenantId
    }
}
