package com.quantumlock.search;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.auth.JwtProperties;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import com.quantumlock.document.DocumentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Provides tenant-isolated searchable encryption using HMAC-SHA256 blind indexes (ADR-005).
 * Keywords are normalized, hashed with a tenant-bound HMAC key, and stored without
 * leaking plaintext terms.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final BlindIndexRepository blindIndexRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;
    private final JwtProperties jwtProperties;

    @Value("${jwt.secret:default-blind-index-key-256-bits-long-minimum}")
    private String indexSecret = "default-blind-index-key-256-bits-long-minimum";

    @Autowired
    public SearchService(BlindIndexRepository blindIndexRepository,
                         DocumentRepository documentRepository,
                         @Autowired(required = false) AuditService auditService,
                         @Autowired(required = false) JwtProperties jwtProperties) {
        this.blindIndexRepository = blindIndexRepository;
        this.documentRepository = documentRepository;
        this.auditService = auditService;
        this.jwtProperties = jwtProperties != null ? jwtProperties : new JwtProperties();
    }

    public SearchService(BlindIndexRepository blindIndexRepository,
                         DocumentRepository documentRepository,
                         AuditService auditService) {
        this(blindIndexRepository, documentRepository, auditService, new JwtProperties());
    }

    public SearchService(BlindIndexRepository blindIndexRepository,
                         DocumentRepository documentRepository,
                         JwtProperties jwtProperties) {
        this(blindIndexRepository, documentRepository, null, jwtProperties);
    }

    /**
     * Indexes search tokens for a document using HMAC-SHA256 blind indexing.
     */
    @Transactional
    public void indexDocument(UUID documentId, String textContent, String docName) {
        UUID tenantId = TenantContext.requireTenantId();

        Set<String> tokens = extractTokens(textContent, docName);
        if (tokens.isEmpty()) return;

        blindIndexRepository.deleteAllByDocumentId(documentId);

        List<BlindIndex> entries = tokens.stream()
                .map(token -> BlindIndex.builder()
                        .tenantId(tenantId)
                        .documentId(documentId)
                        .indexKeyVersion(1)
                        .tokenHmac(computeTokenHmac(tenantId, token))
                        .build())
                .collect(Collectors.toList());

        blindIndexRepository.saveAll(entries);
        log.debug("Indexed {} blind tokens for docId={}", entries.size(), documentId);
    }

    /**
     * Indexes a collection of keywords for a document within a tenant.
     */
    @Transactional
    public void indexKeywords(UUID tenantId, UUID documentId, Collection<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return;
        }

        List<BlindIndex> entries = new ArrayList<>();
        Set<String> uniqueKeywords = keywords.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(k -> !k.isBlank())
                .collect(Collectors.toSet());

        for (String kw : uniqueKeywords) {
            String tokenHmac = computeTokenHmac(tenantId, kw);
            entries.add(BlindIndex.builder()
                    .tenantId(tenantId)
                    .documentId(documentId)
                    .indexKeyVersion(1)
                    .tokenHmac(tokenHmac)
                    .build());
        }

        blindIndexRepository.saveAll(entries);
        log.info("Indexed {} blind tokens for documentId={} in tenantId={}", entries.size(), documentId, tenantId);
    }

    /**
     * Executes blind-index search with AND intersection across all query tokens.
     */
    @Transactional
    public List<DocumentResponse> search(List<String> queryTokens, AppUserDetails principal) {
        UUID tenantId = TenantContext.requireTenantId();

        if (queryTokens == null || queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> cleanedTokens = queryTokens.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .distinct()
                .toList();

        if (cleanedTokens.isEmpty()) {
            return Collections.emptyList();
        }

        // For AND intersection: each token must match the document
        Set<UUID> matchingDocIds = null;

        for (String token : cleanedTokens) {
            String hmac = computeTokenHmac(tenantId, token);
            List<BlindIndex> matches = blindIndexRepository.findAllByTenantIdAndTokenHmac(tenantId, hmac);
            Set<UUID> tokenDocIds = matches.stream()
                    .map(BlindIndex::getDocumentId)
                    .collect(Collectors.toSet());

            if (matchingDocIds == null) {
                matchingDocIds = new HashSet<>(tokenDocIds);
            } else {
                matchingDocIds.retainAll(tokenDocIds);
            }

            if (matchingDocIds.isEmpty()) {
                break;
            }
        }

        if (matchingDocIds == null || matchingDocIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Document> docs = documentRepository.findAllById(matchingDocIds).stream()
                .filter(d -> tenantId.equals(d.getTenantId()))
                .toList();

        if (auditService != null && principal != null) {
            auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                    "SEARCH", null, "SEARCH_EXECUTED", "SUCCESS",
                    "tokensCount=" + cleanedTokens.size() + " resultsCount=" + docs.size());
        }

        return docs.stream()
                .map(d -> DocumentResponse.builder()
                        .id(d.getId())
                        .tenantId(d.getTenantId())
                        .ownerId(d.getOwnerId())
                        .name(d.getName())
                        .classification(d.getClassification())
                        .currentVersion(d.getCurrentVersion())
                        .createdAt(d.getCreatedAt())
                        .updatedAt(d.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Searches documents matching the given keyword in the specified tenant.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> search(UUID tenantId, String query) {
        if (query == null || query.trim().isBlank()) {
            return List.of();
        }

        String tokenHmac = computeTokenHmac(tenantId, query);
        List<BlindIndex> matches = blindIndexRepository.findByTenantIdAndTokenHmac(tenantId, tokenHmac);

        if (matches.isEmpty()) {
            return List.of();
        }

        List<UUID> docIds = matches.stream()
                .map(BlindIndex::getDocumentId)
                .distinct()
                .toList();

        return documentRepository.findAllById(docIds).stream()
                .filter(doc -> tenantId.equals(doc.getTenantId()))
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .tenantId(doc.getTenantId())
                        .ownerId(doc.getOwnerId())
                        .name(doc.getName())
                        .classification(doc.getClassification())
                        .currentVersion(doc.getCurrentVersion())
                        .createdAt(doc.getCreatedAt())
                        .updatedAt(doc.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Computes deterministic HMAC-SHA256 token scoped to the tenant.
     * Different tenants produce completely different HMACs for identical terms.
     */
    public String computeTokenHmac(UUID tenantId, String term) {
        try {
            byte[] tenantKey = deriveTenantHmacKey(tenantId);
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(tenantKey, HMAC_ALGO));
            String normalized = term.trim().toLowerCase();
            byte[] hmacBytes = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute blind index token HMAC", e);
        }
    }

    private byte[] deriveTenantHmacKey(UUID tenantId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String masterSecret = (jwtProperties != null && jwtProperties.getSecret() != null)
                    ? jwtProperties.getSecret()
                    : indexSecret;
            md.update(masterSecret.getBytes(StandardCharsets.UTF_8));
            if (tenantId != null) {
                md.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            }
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    private Set<String> extractTokens(String text, String name) {
        Set<String> tokens = new HashSet<>();
        if (name != null) {
            for (String part : name.split("[^a-zA-Z0-9]+")) {
                if (part.length() >= 2) tokens.add(part.toLowerCase());
            }
        }
        if (text != null) {
            for (String part : text.split("[^a-zA-Z0-9]+")) {
                if (part.length() >= 2) tokens.add(part.toLowerCase());
            }
        }
        return tokens;
    }
}
