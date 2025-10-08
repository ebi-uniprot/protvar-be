package uk.ac.ebi.protvar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.dto.VectorSearchRequest;
import uk.ac.ebi.protvar.dto.VectorSearchResponse;
import uk.ac.ebi.protvar.dto.VectorSearchResult;
import uk.ac.ebi.protvar.service.VectorSearchService;

import java.util.List;
import java.util.Map;

/**
 * REST controller for vector similarity search endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/vector-search")
@RequiredArgsConstructor
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    /**
     * Search using text query (GET)
     * Example: GET /api/vector-search?text=protein function&limit=10
     */
    @GetMapping
    public ResponseEntity<VectorSearchResponse> searchByGet(
            @RequestParam(name = "text") String text,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(VectorSearchResponse.failure("Text parameter is required and cannot be empty"));
        }

        if (limit < 1 || limit > 100) {
            return ResponseEntity
                    .badRequest()
                    .body(VectorSearchResponse.failure("Limit must be between 1 and 100"));
        }

        log.debug("GET vector search request - text: {}, limit: {}", text, limit);
        return performSearch(text, limit);
    }

    /**
     * Search using text query (POST)
     * Example: POST /api/vector-search with body {"text": "protein function", "limit": 10}
     */
    @PostMapping
    public ResponseEntity<VectorSearchResponse> searchByPost(
            @RequestBody VectorSearchRequest request) {

        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(VectorSearchResponse.failure("Text is required and cannot be empty"));
        }

        int limit = request.getLimit() != null ? request.getLimit() : 5;

        if (limit < 1 || limit > 100) {
            return ResponseEntity
                    .badRequest()
                    .body(VectorSearchResponse.failure("Limit must be between 1 and 100"));
        }

        log.debug("POST vector search request - text: {}, limit: {}", request.getText(), limit);
        return performSearch(request.getText(), limit);
    }

    /**
     * Health check for vector search functionality
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth() {
        boolean embeddingServiceHealthy = vectorSearchService.isEmbeddingServiceAvailable();

        if (embeddingServiceHealthy) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "embeddingService", "UP",
                    "database", "UP"
            ));
        } else {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DEGRADED",
                            "embeddingService", "DOWN",
                            "database", "UP",
                            "message", "Vector search unavailable - embedding service is down"
                    ));
        }
    }

    private ResponseEntity<VectorSearchResponse> performSearch(String text, int limit) {
        List<VectorSearchResult> results = vectorSearchService.searchByText(text, limit);

        if (results.isEmpty() && !vectorSearchService.isEmbeddingServiceAvailable()) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(VectorSearchResponse.failure("Embedding service is currently unavailable"));
        }

        return ResponseEntity.ok(VectorSearchResponse.success(text, results, limit));
    }
}
