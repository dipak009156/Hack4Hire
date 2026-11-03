package com.quantumlock.search;

import com.quantumlock.auth.AppUserDetails;
import com.quantumlock.common.TenantContext;
import com.quantumlock.document.DocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/search", "/api/v1/documents/search"})
@RequiredArgsConstructor
@Tag(name = "Searchable Encryption", description = "Tenant-isolated blind-index encrypted search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search documents by exact keyword using HMAC blind index",
               description = "Queries the encrypted blind index using tenant-scoped HMAC-SHA256 tokens.")
    public ResponseEntity<List<DocumentResponse>> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "query", required = false) String query,
            @AuthenticationPrincipal AppUserDetails principal) {

        String searchTerm = (q != null && !q.isBlank()) ? q : query;
        if (searchTerm == null || searchTerm.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        List<String> tokens = Arrays.stream(searchTerm.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (principal != null) {
            return ResponseEntity.ok(searchService.search(tokens, principal));
        }

        UUID tenantId = TenantContext.requireTenantId();
        return ResponseEntity.ok(searchService.search(tenantId, searchTerm));
    }
}
