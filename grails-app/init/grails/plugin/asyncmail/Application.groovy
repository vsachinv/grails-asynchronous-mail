package grails.plugin.asyncmail

import grails.boot.*
import grails.boot.config.GrailsAutoConfiguration
import grails.plugins.mail.MailConfigurationProperties
import grails.plugins.mail.MailMessageBuilderFactory
import grails.plugins.mail.MailService
import grails.plugins.metadata.*
import grails.plugins.tenant.TenantContextProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean

/**
 * Application class for running the plugin standalone and for its integration tests.
 * It is marked @PluginSource and is never bootstrapped by a consuming application, so the
 * bean definitions below only apply to this plugin's own test context.
 */
@PluginSource
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

    /**
     * grails-mail 5.x auto-configures mailService by constructor-injecting a MailMessageBuilderFactory,
     * while mail-oauth registers three beans of that type (mailMessageBuilderFactory,
     * oauthMailMessageBuilderFactory, graphMailMessageBuilderFactory). Pin the default one so the
     * test context can start. Consuming applications provide their own equivalent.
     */
    @Bean
    MailService mailService(MailConfigurationProperties mailConfigurationProperties,
                            @Qualifier('mailMessageBuilderFactory') MailMessageBuilderFactory mailMessageBuilderFactory) {
        new MailService(mailConfigurationProperties, mailMessageBuilderFactory)
    }

    /** mail-oauth's tenantMailService requires a tenantContextProvider bean from the host application. */
    @Bean
    TenantContextProvider tenantContextProvider() {
        return { -> 1L } as TenantContextProvider
    }
}
