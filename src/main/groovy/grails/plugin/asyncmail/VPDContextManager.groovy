package grails.plugin.asyncmail

/**
 * Strategy for switching the database row-security (Oracle VPD) context around a unit of work.
 * <p>
 * The plugin registers {@link DefaultVPDContextManager} as the Spring bean {@code asynchronousMailVPDContextManager}.
 * A host application can replace it by defining its own bean with the same name (e.g. in {@code resources.groovy}
 * or a {@code @Configuration} class) to change the stored procedure, the all-tenant marker, or the restore policy.
 * <p>
 * Contract for every implementation:
 * <ul>
 *   <li>the context is a property of the JDBC connection bound to the current transaction, so callers must open a
 *       scope INSIDE the transaction that performs the queries;</li>
 *   <li>the scope must be restored on exit, even when the body throws;</li>
 *   <li>scopes are per thread: a nested call for the SAME tenant while a scope is active on the same thread must
 *       reuse it and not touch the database again; a nested call for a DIFFERENT tenant must fail with
 *       {@link VPDContextMismatchException} rather than silently switch or reuse the wrong context.</li>
 * </ul>
 */
interface VPDContextManager {

    /** Runs {@code body} with the context switched to all-tenant mode and restores the previous context afterwards. */
    public <T> T withAllTenantVPDContext(Closure<T> body)

    /** Runs {@code body} with the context switched to the given tenant and restores the previous context afterwards. */
    public <T> T withVPDContext(Long tenantId, Closure<T> body)

    /** True while a scope opened by this manager is active on the current thread. */
    boolean isScopeActive()

    /**
     * True while a scope for exactly {@code tenantId} is active on the current thread, false when no scope is active.
     *
     * @throws VPDContextMismatchException when a scope is active but for a different tenant
     */
    boolean isScopeActive(Long tenantId) throws VPDContextMismatchException

    /** The tenant id of the active scope on the current thread, or null when none is active. */
    Long getActiveTenantId()
}
