package grails.plugin.asyncmail

import grails.boot.*
import grails.boot.config.GrailsAutoConfiguration
import grails.plugins.metadata.*
import grails.plugins.tenant.TenantContextProvider
import org.springframework.context.annotation.Bean

@PluginSource
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

    /**
     * mail-oauth's tenantMailService requires a tenantContextProvider bean from the host application.
     * Test-only stub (not bootstrapped by host apps because of @PluginSource); always tenant 1.
     */
    @Bean
    TenantContextProvider tenantContextProvider() {
        return { -> 1L } as TenantContextProvider
    }
}
