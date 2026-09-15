package grails.plugin.asyncmail

import grails.plugins.mail.MailMessageBuilder
import grails.plugins.mail.oauth.TenantMailService
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

/**
 * Test for synchronous send service
 */
class AsynchronousMailSendServiceSpec extends Specification implements ServiceUnitTest<AsynchronousMailSendService> {
    void setup() {
        service.tenantMailService = Mock(TenantMailService)
    }

    def "test send"() {
        given: "a message"
            AsynchronousMailMessage message = new AsynchronousMailMessage(
                    tenantId: 7L,
                    from: 'John Smith <john@example.com>',
                    to: ['Mary Smith <mary@example.com>'],
                    subject: 'Subject',
                    text: 'Text'
            )
        when: "send"
            service.send(message)
        then: "calls tenantMailService.sendMailWithTenant() with the message tenant"
            1 * service.tenantMailService.sendMailWithTenant(7L, _ as Closure)
    }

    def "test text alternative and multipart"() {
        given: 'a message with alternative'
            AsynchronousMailMessage message = new AsynchronousMailMessage(
                    tenantId: 7L,
                    from: 'John Smith <john@example.com>',
                    to: ['Mary Smith <mary@example.com>'],
                    subject: 'Subject',
                    text: '<html>HTML text</html>',
                    html: true,
                    alternative: 'Alternative text'
            )
        and: 'stub method sendMailWithTenant'
            def mockMessageBuilder = Mock(MailMessageBuilder) {
                isMimeCapable() >> true
            }
            service.tenantMailService.sendMailWithTenant(_ as Long, _ as Closure) >> { Long tenantId, Closure cl ->
                cl.delegate = mockMessageBuilder
                cl.call()
            }
        when: "send"
            service.send(message)
        then: 'call tenantMailService with multipart'
            1 * mockMessageBuilder.multipart(true)
    }
}
