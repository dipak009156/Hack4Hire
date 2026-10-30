package com.quantumlock.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for uploading a new document or document version.
 *
 * @param name           human-readable document name (must be unique per tenant)
 * @param classification data classification label
 * @param contentType    MIME type of the plaintext content
 * @param content        Base64-encoded plaintext document bytes
 */
public record UploadDocumentRequest(

        @NotBlank(message = "Document name must not be blank")
        @Size(max = 1024, message = "Document name must not exceed 1024 characters")
        String name,

        @Pattern(regexp = "^(PUBLIC|INTERNAL|CONFIDENTIAL|TOP_SECRET)$",
                 message = "Classification must be one of PUBLIC, INTERNAL, CONFIDENTIAL, TOP_SECRET")
        String classification,

        @NotBlank(message = "contentType must not be blank")
        String contentType,

        @NotBlank(message = "content must not be blank")
        String content
) {
    public String classification() {
        return classification == null ? "INTERNAL" : classification;
    }
}

