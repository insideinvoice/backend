package com.insideinvoice.email;

/**
 * A file to attach to an outgoing email.
 *
 * <p>The email provider (Resend) accepts attachments as base64-encoded content
 * with an explicit filename and MIME type, so the bytes are carried as-is and
 * the filename must already be sanitized by the caller (it lands in a MIME
 * header).</p>
 *
 * @param filename   sanitized filename shown in the email client
 * @param content    raw file bytes (encoded to base64 before sending)
 * @param mimeType   MIME type, e.g. {@code application/pdf}
 */
public record EmailAttachment(String filename, byte[] content, String mimeType) {

    public static EmailAttachment pdf(String filename, byte[] content) {
        return new EmailAttachment(filename, content, "application/pdf");
    }
}
