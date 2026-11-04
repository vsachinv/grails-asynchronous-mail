package grails.plugin.asyncmail

import org.codehaus.groovy.runtime.InvokerHelper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.hibernate.type.LongType

/**
 * Default {@link VPDContextManager}: calls {@code PKG_MART_SET_CONTEXT.SET_CONTEXT(?)} on the CURRENT bound
 * Hibernate session (withSession, not a new one, so transaction visibility and @Rollback tests are preserved).
 * <p>
 * All-tenant mode is {@link #ALL_TENANT_ACCESS_ID} (-99999). On exit the context is restored to the tenant of the
 * host's {@code tenantContextProvider} bean when one is present and returns a value, otherwise to {@code NULL}
 * (the usual case on Quartz job threads).
 * <p>
 * Set and restore both rethrow on failure by design. A restore failure after a body that already failed is logged
 * instead, so the body's exception is the one the caller sees.
 * <p>
 * Disabled entirely by {@code asynchronous.mail.vpd.enabled=false}: every scope then just runs its body, no SQL is
 * issued and no scope is tracked, for host applications whose database has no VPD policy.
 * <p>
 * Registered by the plugin as bean {@code asynchronousMailVPDContextManager}; override that bean in the host
 * application to customise (e.g. a different procedure or marker id).
 */
@Slf4j
@CompileStatic
class DefaultVPDContextManager implements VPDContextManager {

    static final Long ALL_TENANT_ACCESS_ID = -99999L

    /** State of the scope active on this thread: the tenant it switched to and the id to restore on exit. */
    private static class Scope {
        final Long tenantId
        final Long restoreId

        Scope(Long tenantId, Long restoreId) {
            this.tenantId = tenantId
            this.restoreId = restoreId
        }
    }

    /** Non-null while a scope is active on this thread. */
    private static final ThreadLocal<Scope> ACTIVE_SCOPE = new ThreadLocal<>()

    /** Stored procedure invoked with the tenant id (or NULL) as its single parameter. */
    String setContextCall = 'call PKG_MART_SET_CONTEXT.SET_CONTEXT(?)'

    /**
     * Optional bean {@code tenantContextProvider} of the host application, resolved dynamically (any object with a
     * {@code getCurrentTenantId()} method, e.g. mail-oauth's TenantContextProvider). Used only for the restore value.
     */
    Object tenantContextProvider

    /** Reads {@code asynchronous.mail.vpd.enabled}; autowired by the plugin. */
    AsynchronousMailConfigService asynchronousMailConfigService

    /** True unless {@code asynchronous.mail.vpd.enabled=false}. Overridable. */
    boolean isEnabled() {
        return asynchronousMailConfigService == null || asynchronousMailConfigService.vpdEnabled
    }

    @Override
    public <T> T withAllTenantVPDContext(Closure<T> body) {
        return withVPDContext(ALL_TENANT_ACCESS_ID, body)
    }

    @Override
    public <T> T withVPDContext(Long tenantId, Closure<T> body) {
        if (!isEnabled()) {
            log.trace("VPD context handling is disabled (asynchronous.mail.vpd.enabled=false); running body as is.")
            return body.call()
        }
        if (isScopeActive(tenantId)) {
            log.trace("VPD context scope for tenant ${tenantId} already active on this thread; reusing it.")
            return body.call()
        }
        Long restoreId = currentCallerTenantId()
        ACTIVE_SCOPE.set(new Scope(tenantId, restoreId))
        boolean bodyFailed = false
        try {
            setVPDContext(tenantId)
            return body.call()
        } catch (Throwable t) {
            bodyFailed = true
            throw t
        } finally {
            ACTIVE_SCOPE.remove()
            try {
                setVPDContext(restoreId)
            } catch (Exception e) {
                if (bodyFailed) {
                    log.error("Unable to restore VPD context to ${restoreId} after a failed body: ${e.message}")
                } else {
                    throw e
                }
            }
        }
    }

    @Override
    boolean isScopeActive() {
        return ACTIVE_SCOPE.get() != null
    }

    @Override
    boolean isScopeActive(Long tenantId) throws VPDContextMismatchException {
        Scope scope = ACTIVE_SCOPE.get()
        if (scope == null) {
            return false
        }
        if (scope.tenantId != tenantId) {
            throw new VPDContextMismatchException(scope.tenantId, tenantId)
        }
        return true
    }

    @Override
    Long getActiveTenantId() {
        return ACTIVE_SCOPE.get()?.tenantId
    }

    /** The tenant to restore on scope exit; null when the host provides none (job threads). Override to customise. */
    protected Long currentCallerTenantId() {
        try {
            if (tenantContextProvider == null) {
                return null
            }
            return (Long) InvokerHelper.invokeMethod(tenantContextProvider, 'getCurrentTenantId', null)
        } catch (Exception e) {
            log.debug("No caller tenant available from tenantContextProvider: ${e.message}")
            return null
        }
    }

    /** Executes the stored-procedure call on the current session. Override to customise the SQL. */
    @CompileDynamic
    protected void setVPDContext(Long tenantId) {
        try {
            AsynchronousMailMessage.withSession { session ->
                // Explicit type: an untyped null bind (the restore value on job threads) would otherwise be sent
                // as a serializable/binary parameter, which Oracle rejects for the NUMBER argument.
                session.createSQLQuery(setContextCall)
                        .setParameter(1, tenantId, LongType.INSTANCE)
                        .executeUpdate()
            }
        } catch (Exception e) {
            log.error("Unable to set VPD context to ${tenantId} : ${e.message}")
            throw new IllegalStateException("Unable to set VPD context to ${tenantId}")
        }
    }
}
