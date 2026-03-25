package grails.plugin.asyncmail

import groovy.util.logging.Slf4j

@Slf4j
class ExpiredMessagesCollectorJob {
    static triggers = {}
    static description = "Periodically marks unsent emails as EXPIRED if their endDate has passed"

    static concurrent = false
    static group = "AsynchronousMail"

    AsynchronousMailPersistenceService asynchronousMailPersistenceService

    def execute() {
        log.trace('Entering execute method.')
        asynchronousMailPersistenceService.updateExpiredMessages()
        log.trace('Exiting execute method.')
    }
}
