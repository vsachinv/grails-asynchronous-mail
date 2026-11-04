package grails.plugin.asyncmail

import groovy.transform.CompileStatic

import java.util.concurrent.atomic.AtomicInteger

/**
 * Test-only stand-in for the Oracle package PKG_MART_SET_CONTEXT used by
 * DefaultVPDContextManager.
 * Registered in H2 through the INIT clause of the test datasource URL as
 * PKG_MART_SET_CONTEXT.SET_CONTEXT, so the call succeeds on H2 without VPD semantics.
 */
@CompileStatic
class H2VpdContextStub {
    static volatile Long lastContextId
    static final AtomicInteger callCount = new AtomicInteger()

    static void setContext(Long contextId) {
        lastContextId = contextId
        callCount.incrementAndGet()
    }
}
