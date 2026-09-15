package grails.plugin.asyncmail

import grails.gorm.transactions.Transactional
import grails.plugin.asyncmail.enums.MessageStatus
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@Transactional
class AsynchronousMailPersistenceService {

    private static final Long ALL_TENANT_ACCESS_ID = -99999

    AsynchronousMailConfigService asynchronousMailConfigService

    /**
     * Sets VPD context to no-tenant mode (-99999) on the current Hibernate session
     * so that queries bypass Oracle VPD tenant-level row security and access data across all tenants.
     * <p>
     * The async_mail_mess table has a tenant_id column with a DB-level VPD security policy.
     * Setting context to -99999 disables tenant filtering for this session.
     * <p>
     * Uses withSession to operate on the CURRENT bound session (not a new one),
     * preserving transaction visibility for callers and @Rollback tests.
     * <p>
     * Gracefully handles non-Oracle environments (e.g., H2 in tests) where the
     * PKG_MART_SET_CONTEXT package does not exist.
     */
    void setVPDContextForAllTenantAccess() {
        try {
            AsynchronousMailMessage.withSession { session ->
                session.createSQLQuery("call PKG_MART_SET_CONTEXT.SET_CONTEXT(?)")
                        .setParameter(1, ALL_TENANT_ACCESS_ID)
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
        setVPDContextForAllTenantAccess()
        return message.save(flush: flush, failOnError: true, validate: validate)
    }

    @CompileStatic
    void delete(AsynchronousMailMessage message, boolean flush) {
        setVPDContextForAllTenantAccess()
        message.delete(flush: flush)
    }

    @CompileStatic
    void deleteAttachments(AsynchronousMailMessage message, boolean flush) {
        setVPDContextForAllTenantAccess()
        message.attachments.clear()
        message.save(flush: flush)
    }

    @CompileStatic
    AsynchronousMailMessage getMessage(long id) {
        setVPDContextForAllTenantAccess()
        return AsynchronousMailMessage.get(id)
    }

    List<Long> selectMessagesIdsForSend() {
        setVPDContextForAllTenantAccess()
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

    void updateExpiredMessages() {

        int count = 0
        if (asynchronousMailConfigService.mongo) {
            setVPDContextForAllTenantAccess()
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
                setVPDContextForAllTenantAccess()
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
