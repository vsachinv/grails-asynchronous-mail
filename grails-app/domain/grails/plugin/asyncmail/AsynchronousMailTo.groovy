package grails.plugin.asyncmail

class AsynchronousMailTo implements Serializable {

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
        table 'async_mail_to'
        version false
        id generator: 'sequence', params: [sequence_name: 'ASYNC_MAIL_TO_ID_SEQ']
        message column: 'message_id'
        address column: 'to_string', length: MAX_EMAIL_ADDR_SIZE
        position column: 'to_idx'
        tenantId column: 'tenant_id', index: 'idx_async_mail_to_tenant'
    }

    static constraints = {
        address(blank: false, maxSize: MAX_EMAIL_ADDR_SIZE)
        position(nullable: true)
        tenantId(nullable: false)
    }
}
