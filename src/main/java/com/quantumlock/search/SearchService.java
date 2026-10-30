package com.quantumlock.search;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import com.quantumlock.document.DocumentResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final BlindIndexRepository blindIndexRepository;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;

    @Value("${jwt.secret:default-blind-index-key-256-bits-long-minimum}")
    private String indexSecret;

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
                        .tokenHmac(computeHmac(token.toLowerCase().trim()))
                        .build())
                .collect(Collectors.toList());

        blindIndexRepository.saveAll(entries);
        log.debug("Indexed {} blind tokens for docId={}", entries.size(), documentId);
    }

    /**
     * Executes blind-index search with AND intersection across all query tokens.
     */
    @Transactional(readOnly = true)
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
            String hmac = computeHmac(token);
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

        auditService.record(tenantId, principal.getUserId(), principal.getUsername(),
                "SEARCH", null, "SEARCH_EXECUTED", "SUCCESS",
                "tokensCount=" + cleanedTokens.size() + " resultsCount=" + docs.size());

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

    private String computeHmac(String token) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(indexSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(key);
            byte[] hmacBytes = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
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

