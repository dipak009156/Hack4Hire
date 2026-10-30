package com.quantumlock.search;

import com.quantumlock.auth.JwtProperties;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import com.quantumlock.document.DocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private BlindIndexRepository blindIndexRepository;

    @Mock
    private DocumentRepository documentRepository;

    private SearchService searchService;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID docId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
        searchService = new SearchService(blindIndexRepository, documentRepository, jwtProperties);
    }

    @Test
    @DisplayName("indexKeywords stores blind HMAC index records with normalized terms")
    void indexKeywords_storesHmacRecords() {
        Set<String> keywords = Set.of("Oncology", "GENETICS", "dna");

        searchService.indexKeywords(tenantA, docId, keywords);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BlindIndex>> captor = ArgumentCaptor.forClass(List.class);
        verify(blindIndexRepository).saveAll(captor.capture());

        List<BlindIndex> saved = captor.getValue();
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(b -> b.getTenantId().equals(tenantA));
        assertThat(saved).allMatch(b -> b.getDocumentId().equals(docId));
        assertThat(saved).allMatch(b -> b.getTokenHmac() != null && !b.getTokenHmac().isBlank());
    }

    @Test
    @DisplayName("Strict multi-tenant HMAC isolation: same keyword produces different HMAC tokens for different tenants")
    void multiTenantHmacIsolation() {
        String tokenA = searchService.computeTokenHmac(tenantA, "oncology");
        String tokenB = searchService.computeTokenHmac(tenantB, "oncology");

        assertThat(tokenA).isNotEqualTo(tokenB);
    }

    @Test
    @DisplayName("search returns matching documents within tenant and filters out cross-tenant documents")
    void search_returnsOnlyMatchingTenantDocuments() {
        String query = "oncology";
        String tokenHmac = searchService.computeTokenHmac(tenantA, query);

        BlindIndex match = BlindIndex.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantA)
                .documentId(docId)
                .tokenHmac(tokenHmac)
                .build();

        when(blindIndexRepository.findByTenantIdAndTokenHmac(tenantA, tokenHmac))
                .thenReturn(List.of(match));

        Document doc = Document.builder()
                .id(docId)
                .tenantId(tenantA)
                .ownerId(UUID.randomUUID())
                .name("Cancer Research Study")
                .classification("CONFIDENTIAL")
                .currentVersion(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(documentRepository.findAllById(List.of(docId))).thenReturn(List.of(doc));

        List<DocumentResponse> results = searchService.search(tenantA, query);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(docId);
        assertThat(results.get(0).name()).isEqualTo("Cancer Research Study");
    }

    @Test
    @DisplayName("search returns empty list when query is blank or no matches found")
    void search_emptyResults() {
        assertThat(searchService.search(tenantA, "")).isEmpty();
        assertThat(searchService.search(tenantA, "   ")).isEmpty();
        assertThat(searchService.search(tenantA, null)).isEmpty();

        when(blindIndexRepository.findByTenantIdAndTokenHmac(eq(tenantA), any()))
                .thenReturn(List.of());

        assertThat(searchService.search(tenantA, "unknown")).isEmpty();
    }
}
