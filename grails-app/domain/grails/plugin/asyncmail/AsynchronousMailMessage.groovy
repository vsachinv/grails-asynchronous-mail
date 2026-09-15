package grails.plugin.asyncmail


import grails.plugin.asyncmail.enums.MessageStatus
import groovy.transform.ToString
import org.apache.commons.lang.StringUtils

import static grails.plugin.asyncmail.enums.MessageStatus.*

@ToString(includeNames = true, includeFields = true,  includes = 'id,tenantId,subject,to,status')
class AsynchronousMailMessage implements Serializable {
    /**
     * This date is accepted as the max date because different DBMSs store dates in
     * different formats. We can't use a date which is the maximum in Java.
     * I want to believe that my plugin will work in 1000 years. If asynchronous mail plugin
     * works in 1000 years then I or somebody else will change this value.
     */
    private static final MAX_DATE
    static {
        Calendar c = Calendar.getInstance()
        c.set(3000, 0, 1, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        MAX_DATE = c.getTime()
    }

    /** Max length of email address. See the RFC 5321. */
    private static final int MAX_EMAIL_ADDR_SIZE = 256

    /** Id. Need to be declared explicitly for proper @ToString output */
    Long id


    // !!! Message fields !!!
    // Sender attributes
    String from
    String replyTo

    // Envelope from field
    String envelopeFrom

    // Subject and text
    String subject
    String text

    // An alternative text for HTML and text messages
    String alternative
    boolean html = false

    /** Attachments */
    List<AsynchronousMailAttachment> attachments

    // !!! Additional status fields !!!
    /** Message status */
    MessageStatus status = CREATED

    /** Date when message was created */
    Date createDate = new Date()

    /** Date when message was sent */
    Date sentDate

    // Send interval
    Date beginDate = new Date()
    Date endDate = MAX_DATE

    /** Priority. Higher number, higher priority. */
    int priority = 0

    // Attempts
    int attemptsCount = 0
    int maxAttemptsCount = 1
    Date lastAttemptDate

    /** Minimum interval between attempts in milliseconds */
    long attemptInterval = 300000l

    /** Mark this message for deletion after it's sent */
    boolean markDelete = false

    boolean markDeleteAttachments = false

    Long tenantId

    /** Check if message can be aborted */
    boolean isAbortable() {
        return status in [CREATED, ATTEMPTED]
    }

    boolean hasCreatedStatus() {
        return status == CREATED
    }

    boolean hasAttemptedStatus() {
        return status == ATTEMPTED
    }

    boolean hasSentStatus() {
        return status == SENT
    }

    boolean hasErrorStatus() {
        return status == ERROR
    }

    boolean hasExpiredStatus() {
        return status == EXPIRED
    }

    boolean hasAbortStatus() {
        return status == ABORT
    }

    static transients = ['abortable', 'to', 'bcc', 'cc', 'headers']

    /**
     * Public API preservation: callers (builder, send service, downstream apps) read and
     * write {@code to}/{@code bcc}/{@code cc} as {@code List<String>} and {@code headers}
     * as {@code Map<String,String>}. Internally those map onto {@code toEntries},
     * {@code bccEntries}, {@code ccEntries}, {@code headerEntries} which carry their own
     * {@code tenantId}.
     */
    List<String> getTo() {
        toEntries == null ? null : toEntries.sort(false) { it.position ?: 0 }*.address
    }

    void setTo(List<String> list) {
        toEntries?.collect { it }?.each { removeFromToEntries(it) }
        list?.eachWithIndex { String addr, int idx ->
            addToToEntries(new AsynchronousMailTo(address: addr, position: idx))
        }
    }

    List<String> getBcc() {
        bccEntries == null ? null : bccEntries.sort(false) { it.position ?: 0 }*.address
    }

    void setBcc(List<String> list) {
        bccEntries?.collect { it }?.each { removeFromBccEntries(it) }
        list?.eachWithIndex { String addr, int idx ->
            addToBccEntries(new AsynchronousMailBcc(address: addr, position: idx))
        }
    }

    List<String> getCc() {
        ccEntries == null ? null : ccEntries.sort(false) { it.position ?: 0 }*.address
    }

    void setCc(List<String> list) {
        ccEntries?.collect { it }?.each { removeFromCcEntries(it) }
        list?.eachWithIndex { String addr, int idx ->
            addToCcEntries(new AsynchronousMailCc(address: addr, position: idx))
        }
    }

    Map<String, String> getHeaders() {
        headerEntries == null ? null : headerEntries.collectEntries { [(it.name): it.value] }
    }

    void setHeaders(Map<String, String> map) {
        headerEntries?.collect { it }?.each { removeFromHeaderEntries(it) }
        map?.each { String key, String value ->
            addToHeaderEntries(new AsynchronousMailHeader(name: key, value: value))
        }
    }

    static hasMany = [
            attachments   : AsynchronousMailAttachment,
            toEntries     : AsynchronousMailTo,
            bccEntries    : AsynchronousMailBcc,
            ccEntries     : AsynchronousMailCc,
            headerEntries : AsynchronousMailHeader
    ]

    static mapping = {
        table 'async_mail_mess'

        from column: 'from_column'
        tenantId column: 'tenant_id', index: 'idx_async_mail_tenant'

        text type: 'text'

        alternative type: 'text'

        attachments cascade: 'all-delete-orphan'
        toEntries cascade: 'all-delete-orphan'
        bccEntries cascade: 'all-delete-orphan'
        ccEntries cascade: 'all-delete-orphan'
        headerEntries cascade: 'all-delete-orphan'
    }

    static constraints = {
        def mailboxValidator = { String value ->
            return value == null || Validator.isMailbox(value)
        }
        tenantId(nullable: false)

        // Message fields
        from(nullable: true, maxSize: MAX_EMAIL_ADDR_SIZE, validator: mailboxValidator)
        replyTo(nullable: true, maxSize: MAX_EMAIL_ADDR_SIZE, validator: mailboxValidator)

        // The validator for list of email addresses
        def emailList = { List<String> list, reference, errors ->
            boolean flag = true
            if (list != null) {
                list.each { String addr ->
                    if (!Validator.isMailbox(addr)) {
                        errors.rejectValue(propertyName, 'asynchronous.mail.mailbox.invalid')
                        flag = false
                    }
                }
            }
            return flag
        }

        def atLeastOneRecipientValidator = { List<String> value, reference, errors ->
            // It's needed to access to propertyName
            emailList.delegate = delegate

            // Validate address list
            if (!emailList(value, reference, errors)) {
                return false
            }

            boolean hasRecipients = reference.to || reference.cc || reference.bcc
            if (!hasRecipients) {
                errors.reject('asynchronous.mail.one.recipient.required')
            }
            return hasRecipients
        }

        // The nullable constraint isn't applied for collections by default.
        to(nullable: true, validator: atLeastOneRecipientValidator)
        cc(nullable: true, validator: emailList)
        bcc(nullable: true, validator: emailList)

        headers(nullable: true, validator: { Map<String, String> map ->
            boolean flag = true
            map?.each { String key, String value ->
                if (StringUtils.isBlank(key) || StringUtils.isBlank(value)) {
                    flag = false
                }
            }
            return flag
        })

        envelopeFrom(nullable: true, maxSize: MAX_EMAIL_ADDR_SIZE, validator: mailboxValidator)

        subject(blank: false, maxSize: 988)
        text(blank: false)
        alternative(nullable: true)

        // Status fields
        //status()
        //createDate()
        sentDate(nullable: true)
        //beginDate()
        endDate(validator: { Date val, AsynchronousMailMessage mess ->
            val && mess.beginDate && val.after(mess.beginDate)
        })

        // Attempt fields
        attemptsCount(min: 0)
        maxAttemptsCount(min: 1)
        lastAttemptDate(nullable: true)
        attemptInterval(min: 0l)

    }
}
