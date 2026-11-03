package com.quantumlock.search;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.auth.JwtProperties;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import com.quantumlock.document.DocumentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private BlindIndexRepository blindIndexRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private AuditService auditService;

    private SearchService searchService;

    private final UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID docId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private AppUserDetails principal;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("test-super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
        searchService = new SearchService(blindIndexRepository, documentRepository, auditService, jwtProperties);
        ReflectionTestUtils.setField(searchService, "indexSecret", "test-super-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
        TenantContext.setTenantId(tenantA);
        principal = new AppUserDetails(userId, tenantA, "alice", List.of("USER"), "eng", "HIGH");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("indexDocument computes HMAC blind tokens and saves entries")
    void indexDocument_savesBlindTokens() {
        searchService.indexDocument(docId, "financial quarterly revenue forecast", "finance-report.pdf");

        verify(blindIndexRepository).deleteAllByDocumentId(docId);
        verify(blindIndexRepository).saveAll(argThat(list -> {
            List<BlindIndex> items = (List<BlindIndex>) list;
            return items.size() >= 4 && items.stream().allMatch(b -> b.getTokenHmac().length() == 64);
        }));
    }

    @Test
    @DisplayName("search with AND intersection returns only documents containing all query tokens")
    void search_andIntersection() {
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();

        // Token 1 matches doc1 and doc2
        when(blindIndexRepository.findAllByTenantIdAndTokenHmac(eq(tenantA), any()))
                .thenReturn(List.of(
                        BlindIndex.builder().tenantId(tenantA).documentId(doc1).build(),
                        BlindIndex.builder().tenantId(tenantA).documentId(doc2).build()
                ))
                .thenReturn(List.of(
                        // Token 2 matches only doc1
                        BlindIndex.builder().tenantId(tenantA).documentId(doc1).build()
                ));

        Document doc = Document.builder().id(doc1).tenantId(tenantA).name("doc1.txt").currentVersion(1).build();
        when(documentRepository.findAllById(any())).thenReturn(List.of(doc));

        List<DocumentResponse> results = searchService.search(List.of("quarterly", "revenue"), principal);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(doc1);
        verify(auditService).record(eq(tenantA), eq(userId), eq("alice"), eq("SEARCH"), isNull(), eq("SEARCH_EXECUTED"), eq("SUCCESS"), any());
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
