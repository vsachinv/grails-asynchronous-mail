package grails.plugin.asyncmail

class AsynchronousMailAttachment implements Serializable {

    static final DEFAULT_MIME_TYPE = 'application/octet-stream'

    private static final SIZE_30_MB = 30*1024*1024

    String attachmentName
    String mimeType = DEFAULT_MIME_TYPE
    byte[] content
    boolean inline = false
    Long tenantId

    def beforeValidate() {
        if (tenantId == null){
            tenantId = message?.tenantId
        }
    }

    static belongsTo = [message:AsynchronousMailMessage]

    static mapping = {
        table 'async_mail_attachment'
        tenantId column: 'tenant_id', index: 'idx_async_mail_attachment_tenant'
        version false
    }

    static constraints = {
        attachmentName(blank:false)
        //mimeType()
        content(maxSize:SIZE_30_MB)
        tenantId(nullable: false)
    }
}
