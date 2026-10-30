package com.quantumlock.search;

import com.quantumlock.auth.AppUserDetails;
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

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Blind-index encrypted search with AND intersection")
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'AUDITOR')")
    @Operation(summary = "Search documents by exact keyword tokens (AND intersection)",
               description = "Queries the blind index via HMAC-SHA256 tokens without exposing search terms to the database.")
    public ResponseEntity<List<DocumentResponse>> search(
            @RequestParam(name = "q", required = false) String q,
            @AuthenticationPrincipal AppUserDetails principal) {

        if (q == null || q.isBlank()) {
            return ResponseEntity.ok(List.of());
        }

        List<String> tokens = Arrays.stream(q.split("[,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return ResponseEntity.ok(searchService.search(tokens, principal));
    }
}

