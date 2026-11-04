package grails.plugin.asyncmail

import grails.gorm.transactions.Transactional
import grails.plugin.asyncmail.enums.MessageStatus
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import groovy.transform.CompileDynamic

@Slf4j
@Transactional
class AsynchronousMailPersistenceService {

    AsynchronousMailConfigService asynchronousMailConfigService

    /** Pluggable VPD handling; the plugin registers DefaultVPDContextManager, host apps may override the bean. */
    VPDContextManager asynchronousMailVPDContextManager

    /**
     * Convenience delegate to {@link VPDContextManager#withAllTenantVPDContext}: runs {@code body} in all-tenant
     * mode and restores the caller's context afterwards. Must be called inside the transaction that runs the
     * queries. The CRUD methods of this service deliberately do NOT open a scope themselves: when
     * {@code sendMail {}} is invoked from a request/service thread they run on the caller's session and connection,
     * and switching it to all-tenant mode would override the caller's tenant for the rest of their transaction.
     */
    @CompileStatic
    public <T> T withAllTenantVPDContext(Closure<T> body) {
        return asynchronousMailVPDContextManager.withAllTenantVPDContext(body)
    }

    /** Convenience delegate to {@link VPDContextManager#withVPDContext}. */
    @CompileStatic
    public <T> T withVPDContext(Long tenantId, Closure<T> body) {
        return asynchronousMailVPDContextManager.withVPDContext(tenantId, body)
    }

    /**
     * Sets the VPD context to all-tenant mode on the current session WITHOUT restoring it afterwards and without
     * going through the {@link VPDContextManager} bean.
     *
     * @deprecated use {@link #withAllTenantVPDContext} so the caller's context is restored.
     */
    @Deprecated
    @CompileDynamic
    void setVPDContextForAllTenantAccess() {
        try {
            AsynchronousMailMessage.withSession { session ->
                session.createSQLQuery("call PKG_MART_SET_CONTEXT.SET_CONTEXT(?)")
                        .setParameter(1, DefaultVPDContextManager.ALL_TENANT_ACCESS_ID)
                        .executeUpdate()
            }
        } catch (Exception e) {
            log.error("Unable to set VPD no-tenant context : ${e.message}")
            throw new Exception("Unable to set VPD no-tenant context")
        }
    }

    @CompileStatic
    AsynchronousMailMessage save(
            AsynchronousMailMessage message, boolean flush, boolean validate
    ) {
        // Runs under the caller's tenant context; job-driven callers set the all-tenant VPD context themselves.
        return message.save(flush: flush, failOnError: true, validate: validate)
    }

    @CompileStatic
    void delete(AsynchronousMailMessage message, boolean flush) {
        message.delete(flush: flush)
    }

    @CompileStatic
    void deleteAttachments(AsynchronousMailMessage message, boolean flush) {
        message.attachments.clear()
        message.save(flush: flush)
    }

    @CompileStatic
    AsynchronousMailMessage getMessage(long id) {
        return AsynchronousMailMessage.get(id)
    }

    List<Long> selectMessagesIdsForSend() {
        return AsynchronousMailMessage.withCriteria {
            Date now = new Date()
            lt('beginDate', now)
            gt('endDate', now)
            or {
                eq('status', MessageStatus.CREATED)
                eq('status', MessageStatus.ATTEMPTED)
            }
            order('priority', 'desc')
            order('endDate', 'asc')
            order('attemptsCount', 'asc')
            order('beginDate', 'asc')
            maxResults(asynchronousMailConfigService.messagesAtOnce)
            projections {
                if (asynchronousMailConfigService.mongo) {
                    id()
                } else {
                    property('id')
                }
            }
        } as List<Long>
    }

    /**
     * Bulk-expires CREATED/ATTEMPTED messages past their endDate. Runs under the caller's VPD context;
     * ExpiredMessagesCollectorJob opens the all-tenant scope around it.
     */
    void updateExpiredMessages() {
        int count = 0
        if (asynchronousMailConfigService.mongo) {
            AsynchronousMailMessage.withCriteria {
                lt "endDate", new Date()
                or {
                    eq "status", MessageStatus.CREATED
                    eq "status", MessageStatus.ATTEMPTED
                }
            }.each {
                it.status = MessageStatus.EXPIRED
                it.save(flush: true)
                count++
            }
        } else {
            // This could be done also with the above code.
            AsynchronousMailMessage.withTransaction {
                count = AsynchronousMailMessage.executeUpdate(
                        "update AsynchronousMailMessage amm set amm.status=:es where amm.endDate<:date and (amm.status=:cs or amm.status=:as)",
                        ["es": MessageStatus.EXPIRED, "date": new Date(), "cs": MessageStatus.CREATED, "as": MessageStatus.ATTEMPTED]
                )
            }
        }
        log.trace("${count} expired messages were updated.")
    }

    void flush() {
        AsynchronousMailMessage.withSession { session ->
            session.flush()
        }
    }
}
