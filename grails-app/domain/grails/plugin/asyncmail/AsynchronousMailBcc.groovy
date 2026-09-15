package grails.plugin.asyncmail

class AsynchronousMailBcc implements Serializable {

    private static final int MAX_EMAIL_ADDR_SIZE = 256

    String address
    Integer position = 0
    Long tenantId

    static belongsTo = [message: AsynchronousMailMessage]

    def beforeValidate() {
        if (tenantId == null) {
            tenantId = message?.tenantId
        }
    }

    static mapping = {
        table 'async_mail_bcc'
        version false
        id generator: 'sequence', params: [sequence_name: 'ASYNC_MAIL_BCC_ID_SEQ']
        message column: 'message_id'
        address column: 'bcc_string', length: MAX_EMAIL_ADDR_SIZE
        position column: 'bcc_idx'
        tenantId column: 'tenant_id', index: 'idx_async_mail_bcc_tenant'
    }

    static constraints = {
        address(blank: false, maxSize: MAX_EMAIL_ADDR_SIZE)
        position(nullable: true)
        tenantId(nullable: false)
    }
}
