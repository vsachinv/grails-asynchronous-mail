package grails.plugin.asyncmail

class AsynchronousMailHeader implements Serializable {

    String name
    String value
    Long tenantId

    static belongsTo = [message: AsynchronousMailMessage]

    def beforeValidate() {
        if (tenantId == null) {
            tenantId = message?.tenantId
        }
    }

    static mapping = {
        table 'async_mail_header'
        version false
        id generator: 'sequence', params: [sequence_name: 'ASYNC_MAIL_HEADER_ID_SEQ']
        message column: 'message_id'
        name column: 'header_name', length: 255
        value column: 'header_value'
        tenantId column: 'tenant_id', index: 'idx_async_mail_header_tenant'
    }

    static constraints = {
        name(blank: false, maxSize: 255)
        value(blank: false)
        tenantId(nullable: false)
    }
}
