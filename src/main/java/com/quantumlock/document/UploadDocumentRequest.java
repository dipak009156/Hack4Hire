package com.quantumlock.document;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Request body for uploading a new document or document version.
 */
public record UploadDocumentRequest(

        @Size(max = 1024, message = "Document name must not exceed 1024 characters")
        @JsonProperty("name")
        String name,

        @Pattern(regexp = "^(PUBLIC|INTERNAL|CONFIDENTIAL|TOP_SECRET)$",
                 message = "Classification must be one of PUBLIC, INTERNAL, CONFIDENTIAL, TOP_SECRET")
        @JsonProperty("classification")
        String classification,

        @JsonProperty("contentType")
        String contentType,

        @JsonAlias({"base64Content", "data"})
        @JsonProperty("content")
        String content,

        @JsonProperty("keywords")
        List<String> keywords
) {
    public UploadDocumentRequest(String name, String classification, String contentType, String content) {
        this(name, classification, contentType, content, List.of());
    }

    public String name() {
        return (name == null || name.isBlank()) ? "doc-" + UUID.randomUUID().toString().substring(0, 8) : name;
    }

    public String classification() {
        return classification == null ? "INTERNAL" : classification;
    }

    public String contentType() {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }

    public String content() {
        return content == null ? "" : content;
    }

    public List<String> keywords() {
        return keywords == null ? List.of() : keywords;
    }
}
