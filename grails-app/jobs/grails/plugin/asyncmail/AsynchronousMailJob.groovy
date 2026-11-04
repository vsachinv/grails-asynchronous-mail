package grails.plugin.asyncmail

import groovy.util.logging.Slf4j

/**
 * Send asynchronous messages
 */
@Slf4j
class AsynchronousMailJob {
    static triggers = {}
    static description = "Periodically picks up pending/retry emails from the database and sends them via SMTP/GRAPH across all tenants"

    static concurrent = false
    static group = "AsynchronousMail"

    // Dependency injection
    AsynchronousMailProcessService asynchronousMailProcessService
    VPDContextManager asynchronousMailVPDContextManager

    /**
     * Job-driven entry point: this is the only place (besides the per-message worker transaction in
     * AsynchronousMailProcessService.processEmailMessage) where the all-tenant VPD context is opened.
     * The scope must sit inside a transaction because the VPD context lives on the JDBC connection bound to it.
     * Worker tasks run on other threads/sessions/connections and therefore open their own scope per message.
     */
    def execute() {
        log.trace('Entering execute method.')
        def startDate = System.currentTimeMillis()

        AsynchronousMailMessage.withTransaction {
            asynchronousMailVPDContextManager.withAllTenantVPDContext {
                asynchronousMailProcessService.findAndSendEmails()
            }
        }

        def endDate = System.currentTimeMillis()
        log.trace("Exiting execute method. Execution time = ${endDate - startDate}ms")
    }
}
