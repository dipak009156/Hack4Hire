package com.quantumlock.search;

import com.quantumlock.audit.AuditService;
import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.Document;
import com.quantumlock.document.DocumentRepository;
import com.quantumlock.document.DocumentResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
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

    @Mock
    private AuditService auditService;

    private SearchService searchService;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final AppUserDetails principal = new AppUserDetails(userId, tenantId, "alice", List.of("USER"), "eng", "HIGH");

    @BeforeEach
    void setUp() {
        searchService = new SearchService(blindIndexRepository, documentRepository, auditService);
        ReflectionTestUtils.setField(searchService, "indexSecret", "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha256");
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("indexDocument computes HMAC blind tokens and saves entries")
    void indexDocument_savesBlindTokens() {
        UUID docId = UUID.randomUUID();
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
        when(blindIndexRepository.findAllByTenantIdAndTokenHmac(eq(tenantId), any()))
                .thenReturn(List.of(
                        BlindIndex.builder().tenantId(tenantId).documentId(doc1).build(),
                        BlindIndex.builder().tenantId(tenantId).documentId(doc2).build()
                ))
                .thenReturn(List.of(
                        // Token 2 matches only doc1
                        BlindIndex.builder().tenantId(tenantId).documentId(doc1).build()
                ));

        Document doc = Document.builder().id(doc1).tenantId(tenantId).name("doc1.txt").currentVersion(1).build();
        when(documentRepository.findAllById(any())).thenReturn(List.of(doc));

        List<DocumentResponse> results = searchService.search(List.of("quarterly", "revenue"), principal);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(doc1);
        verify(auditService).record(eq(tenantId), eq(userId), eq("alice"), eq("SEARCH"), isNull(), eq("SEARCH_EXECUTED"), eq("SUCCESS"), any());
    }
}

