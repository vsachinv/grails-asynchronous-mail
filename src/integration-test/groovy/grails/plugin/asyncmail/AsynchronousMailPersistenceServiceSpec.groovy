package grails.plugin.asyncmail

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

import static grails.plugin.asyncmail.enums.MessageStatus.EXPIRED

/**
 * @author Vitalii Samolovskikh aka Kefir, Puneet Behl
 */
@Integration
@Rollback
class AsynchronousMailPersistenceServiceSpec extends Specification {

    @Autowired
    AsynchronousMailPersistenceService asynchronousMailPersistenceService
    @Autowired
    VPDContextManager asynchronousMailVPDContextManager

    /** The 6.x test Application has no tenantContextProvider bean; emulate a caller on tenant 1 for the restore. */
    void setup() {
        ((DefaultVPDContextManager) asynchronousMailVPDContextManager).tenantContextProvider = [getCurrentTenantId: { -> 1L }]
    }

    void cleanup() {
        ((DefaultVPDContextManager) asynchronousMailVPDContextManager).tenantContextProvider = null
    }

    void testCycle() {
        given: "a message"
            AsynchronousMailMessage message = new AsynchronousMailMessage(
                    from: 'John Smith <john@example.com>',
                    to: ['Mary Smith <mary@example.com>'],
                    subject: 'Subject',
                    text: 'Text'
            )
        when: 'a message is instantiated and saved'
            //message.save flush
            asynchronousMailPersistenceService.save(message, true, true)
            List<Long> ids = asynchronousMailPersistenceService.selectMessagesIdsForSend()

        then: 'selectMessagesIdsForSend should return list with 1 messageId'
            1 == ids.size()
            ids[0] == message.id

        when: "get message"
            AsynchronousMailMessage message1 = asynchronousMailPersistenceService.getMessage(ids[0])

        then: "message1 id message"
            message1.id == message.id
            message1.from == 'John Smith <john@example.com>'
            message1.to == ['Mary Smith <mary@example.com>']
            message1.subject == 'Subject'
            message1.text == 'Text'

        when: 'deleted the message'
            asynchronousMailPersistenceService.delete(message1, true)

        then: 'message to be send count should be 0'
            0 == asynchronousMailPersistenceService.selectMessagesIdsForSend()?.size()
    }

    void testDeleteAttachments() {
        given: "a message"
            AsynchronousMailMessage message = new AsynchronousMailMessage(
                    from: 'John Smith <john@example.com>',
                    to: ['Mary Smith <mary@example.com>'],
                    subject: 'Subject',
                    text: 'Text',
                    attachments: [
                            new AsynchronousMailAttachment(
                                    attachmentName: 'name',
                                    content: 'Grails'.getBytes()
                            )
                    ]
            )
        when: 'a message is instantiated and saved'

            asynchronousMailPersistenceService.save(message, true, true)
            List<Long> ids = asynchronousMailPersistenceService.selectMessagesIdsForSend()

        then: 'selectMessagesIdsForSend should return list with 1 messageId'
            1 == ids.size()

        when: "get message"
            AsynchronousMailMessage message1 = asynchronousMailPersistenceService.getMessage(ids[0])

        then: "it contains attachments"
            message1.attachments.size() == 1

        when: 'deleted the message'
            asynchronousMailPersistenceService.deleteAttachments(message, true)
            message1 = asynchronousMailPersistenceService.getMessage(ids[0])

        then: 'the message should have no attachments'
            message1.attachments ? false : true
    }

    void testSaveSimpleMessage() {
        setup:
            AsynchronousMailMessage message = new AsynchronousMailMessage(
                    bcc: ['mary@example.com'],
                    subject: 'Subject',
                    text: 'Text'
            )

        when:
            asynchronousMailPersistenceService.save(message, true, true)
        then:
            AsynchronousMailMessage dbMessage = asynchronousMailPersistenceService.getMessage(message.id)
            dbMessage.bcc == ['mary@example.com']

        cleanup:
            asynchronousMailPersistenceService.delete(message, true)
    }

    void testUpdateExpiredMessages() {
        when: 'message is saved with expired endDate'
            AsynchronousMailMessage message = new AsynchronousMailMessage(
                    from: 'John Smith <john@example.com>',
                    to: ['Mary Smith <mary@example.com>'],
                    subject: 'Subject',
                    text: 'Text',
                    beginDate: new Date(System.currentTimeMillis() - 2),
                    endDate: new Date(System.currentTimeMillis() - 1)
            )
            asynchronousMailPersistenceService.save message, true, true

        then: 'selectMessagesIdsForSend should return empty list'
            0 == asynchronousMailPersistenceService.selectMessagesIdsForSend()?.size()

        when:
            asynchronousMailPersistenceService.updateExpiredMessages()
            message.refresh()

        then:
            EXPIRED == message.status
    }

    void "withAllTenantVPDContext switches to all-tenant mode and restores the caller tenant afterwards"() {
        given:
            H2VpdContextStub.callCount.set(0)
            H2VpdContextStub.lastContextId = null
            Long seenInside = null

        when:
            String result = asynchronousMailPersistenceService.withAllTenantVPDContext {
                seenInside = H2VpdContextStub.lastContextId
                'done'
            }

        then: 'body ran in all-tenant mode'
            result == 'done'
            seenInside == -99999L
        and: 'context restored to the tenant of the emulated tenantContextProvider (1L)'
            H2VpdContextStub.lastContextId == 1L
        and: 'exactly one set and one restore'
            H2VpdContextStub.callCount.get() == 2
    }

    void "nested withAllTenantVPDContext calls reuse the active scope and do not set the context again"() {
        given:
            H2VpdContextStub.callCount.set(0)

        when:
            asynchronousMailPersistenceService.withAllTenantVPDContext {
                asynchronousMailPersistenceService.withAllTenantVPDContext {
                    asynchronousMailPersistenceService.withAllTenantVPDContext { 'inner' }
                }
                asynchronousMailPersistenceService.updateExpiredMessages()
            }

        then: 'only the outermost scope touched the DB context'
            H2VpdContextStub.callCount.get() == 2
            !asynchronousMailVPDContextManager.isScopeActive()
    }

    void "withAllTenantVPDContext restores the context when the body throws"() {
        given:
            H2VpdContextStub.callCount.set(0)

        when:
            asynchronousMailPersistenceService.withAllTenantVPDContext {
                throw new IllegalStateException('boom')
            }

        then:
            thrown(IllegalStateException)
            H2VpdContextStub.lastContextId == 1L
            H2VpdContextStub.callCount.get() == 2
            !asynchronousMailVPDContextManager.isScopeActive()
    }

    void "a nested scope for a different tenant fails with a mismatch exception and the outer scope is still restored"() {
        given:
            H2VpdContextStub.callCount.set(0)

        when:
            asynchronousMailVPDContextManager.withAllTenantVPDContext {
                assert asynchronousMailVPDContextManager.activeTenantId == -99999L
                assert asynchronousMailVPDContextManager.isScopeActive(-99999L)
                asynchronousMailVPDContextManager.withVPDContext(42L) { 'must not run' }
            }

        then:
            VPDContextMismatchException e = thrown()
            e.activeTenantId == -99999L
            e.requestedTenantId == 42L
            H2VpdContextStub.lastContextId == 1L
            H2VpdContextStub.callCount.get() == 2
            !asynchronousMailVPDContextManager.isScopeActive()
            asynchronousMailVPDContextManager.activeTenantId == null
    }

    void "withAllTenantVPDContext is a pass-through when asynchronous.mail.vpd.enabled is false"() {
        given:
            H2VpdContextStub.callCount.set(0)
            DefaultVPDContextManager disabled = new DefaultVPDContextManager(
                    asynchronousMailConfigService: Stub(AsynchronousMailConfigService) { isVpdEnabled() >> false }
            )
            boolean scopeSeenInside = true

        when:
            String result = disabled.withAllTenantVPDContext {
                scopeSeenInside = disabled.scopeActive
                'ran'
            }

        then:
            result == 'ran'
            !scopeSeenInside
            H2VpdContextStub.callCount.get() == 0
            !disabled.enabled
            asynchronousMailVPDContextManager.enabled
    }
}
