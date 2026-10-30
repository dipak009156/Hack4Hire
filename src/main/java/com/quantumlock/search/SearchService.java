package com.quantumlock.search;

import com.quantumlock.auth.JwtProperties;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import com.quantumlock.document.DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RequiredArgsConstructor
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String HMAC_ALGO = "HmacSHA256";

    private final BlindIndexRepository blindIndexRepository;
    private final DocumentRepository documentRepository;
    private final JwtProperties jwtProperties;

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
     * Different tenants will produce completely different HMACs for the identical term.
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
            String masterSecret = jwtProperties.getSecret() != null ? jwtProperties.getSecret() : "default-master-blind-index-key-256";
            md.update(masterSecret.getBytes(StandardCharsets.UTF_8));
            md.update(tenantId.toString().getBytes(StandardCharsets.UTF_8));
            return md.digest();
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed", e);
        }
    }
}
