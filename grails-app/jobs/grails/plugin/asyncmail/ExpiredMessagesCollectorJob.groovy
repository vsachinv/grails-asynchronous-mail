package grails.plugin.asyncmail

import groovy.util.logging.Slf4j

@Slf4j
class ExpiredMessagesCollectorJob {
    static triggers = {}
    static description = "Periodically marks unsent emails as EXPIRED if their endDate has passed"

    static concurrent = false
    static group = "AsynchronousMail"

    AsynchronousMailPersistenceService asynchronousMailPersistenceService
    VPDContextManager asynchronousMailVPDContextManager

    /** Job-driven: opens the all-tenant VPD scope inside the transaction that runs the bulk update. */
    def execute() {
        log.trace('Entering execute method.')
        AsynchronousMailMessage.withTransaction {
            asynchronousMailVPDContextManager.withAllTenantVPDContext {
                asynchronousMailPersistenceService.updateExpiredMessages()
            }
        }
        log.trace('Exiting execute method.')
    }
}
