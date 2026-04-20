package uk.ac.ebi.protvar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.dto.VectorSearchRequest;
import uk.ac.ebi.protvar.dto.VectorSearchResponse;
import uk.ac.ebi.protvar.dto.VectorSearchResult;
import uk.ac.ebi.protvar.service.VectorSearchService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/vector-search")
@RequiredArgsConstructor
public class VectorSearchController {

    private final VectorSearchService vectorSearchService;

    @Value("${embedding.model:biobert}")
    private String defaultModel;

    @GetMapping
    public ResponseEntity<VectorSearchResponse> searchByGet(
            @RequestParam(name = "text") String text,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "model", required = false) String model) {

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("Text parameter is required and cannot be empty"));
        }
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("Limit must be between 1 and 100"));
        }
        if (offset < 0) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("Offset must be non-negative"));
        }

        String resolvedModel = model != null ? model : defaultModel;
        log.debug("GET vector search - text: {}, limit: {}, offset: {}, model: {}", text, limit, offset, resolvedModel);
        return performSearch(text, limit, offset, resolvedModel);
    }

    @PostMapping
    public ResponseEntity<VectorSearchResponse> searchByPost(@RequestBody VectorSearchRequest request) {
        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("Text is required and cannot be empty"));
        }

        int limit = request.getLimit() != null ? request.getLimit() : 10;
        int offset = request.getOffset() != null ? request.getOffset() : 0;
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("Limit must be between 1 and 100"));
        }

        String resolvedModel = request.getModel() != null ? request.getModel() : defaultModel;
        log.debug("POST vector search - text: {}, limit: {}, offset: {}, model: {}", request.getText(), limit, offset, resolvedModel);
        return performSearch(request.getText(), limit, offset, resolvedModel);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkHealth(
            @RequestParam(name = "model", required = false) String model) {
        String resolvedModel = model != null ? model : defaultModel;
        boolean healthy = vectorSearchService.isEmbeddingServiceAvailable(resolvedModel);

        if (healthy) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "embeddingService", "UP",
                    "model", resolvedModel,
                    "database", "UP"
            ));
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DEGRADED",
                            "embeddingService", "DOWN",
                            "model", resolvedModel,
                            "database", "UP",
                            "message", "Vector search unavailable - embedding service is down"
                    ));
        }
    }

    private ResponseEntity<VectorSearchResponse> performSearch(String text, int limit, int offset, String model) {
        List<VectorSearchResult> results = vectorSearchService.searchByText(text, limit, offset, model);

        if (results.isEmpty() && !vectorSearchService.isEmbeddingServiceAvailable(model)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(VectorSearchResponse.failure("Embedding service is currently unavailable for model: " + model));
        }

        return ResponseEntity.ok(VectorSearchResponse.success(text, results, limit, offset, model));
    }
}
