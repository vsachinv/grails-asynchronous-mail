package grails.plugin.asyncmail

import groovy.transform.CompileStatic

/**
 * Test-only stand-in for the Oracle package PKG_MART_SET_CONTEXT used by
 * AsynchronousMailPersistenceService.setVPDContextForAllTenantAccess().
 * Registered in H2 through the INIT clause of the test datasource URL as
 * PKG_MART_SET_CONTEXT.SET_CONTEXT, so the call succeeds on H2 without VPD semantics.
 */
@CompileStatic
class H2VpdContextStub {
    static volatile Long lastContextId

    static void setContext(Long contextId) {
        lastContextId = contextId
    }
}
