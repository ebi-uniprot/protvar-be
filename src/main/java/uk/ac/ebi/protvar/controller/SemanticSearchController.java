package uk.ac.ebi.protvar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.config.ModelRegistryProperties;
import uk.ac.ebi.protvar.dto.*;
import uk.ac.ebi.protvar.service.EmbeddingService;
import uk.ac.ebi.protvar.service.VectorSearchService;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/semantic-search")
@RequiredArgsConstructor
public class SemanticSearchController {

    private final VectorSearchService vectorSearchService;
    private final EmbeddingService embeddingService;
    private final ModelRegistryProperties modelRegistry;

    // ── Model registry ────────────────────────────────────────────────────────

    @GetMapping("/models")
    public ResponseEntity<List<ModelInfo>> getModels() {
        String defaultModelId = modelRegistry.getDefaultModel();
        List<ModelInfo> models = modelRegistry.getModels().entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .map(e -> new ModelInfo(
                        e.getKey(),
                        e.getValue().getLabel(),
                        e.getValue().getDescription(),
                        e.getKey().equals(defaultModelId)
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(models);
    }

    // ── Vector search ─────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<VectorSearchResponse> search(
            @RequestParam String text,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) String model) {

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("text is required"));
        }
        if (limit < 1 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("limit must be between 1 and 100"));
        }
        if (offset < 0) {
            return ResponseEntity.badRequest()
                    .body(VectorSearchResponse.failure("offset must be non-negative"));
        }

        String resolvedModel = model != null ? model : vectorSearchService.getDefaultModel();
        log.debug("semantic search - text: {}, limit: {}, offset: {}, model: {}", text, limit, offset, resolvedModel);

        List<VectorSearchResult> results = vectorSearchService.searchByText(text, limit, offset, resolvedModel);
        if (results.isEmpty() && !vectorSearchService.isEmbeddingServiceAvailable(resolvedModel)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(VectorSearchResponse.failure("Embedding service unavailable for model: " + resolvedModel));
        }
        return ResponseEntity.ok(VectorSearchResponse.success(text, results, limit, offset, resolvedModel));
    }

    // ── Embedding ─────────────────────────────────────────────────────────────

    @GetMapping("/embed")
    public ResponseEntity<EmbeddingResponse> embed(
            @RequestParam String text,
            @RequestParam(required = false) String model) {

        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(EmbeddingResponse.failure(text, "text is required"));
        }

        String resolvedModel = model != null ? model : modelRegistry.getDefaultModel();
        log.debug("embed - text: {}, model: {}", text, resolvedModel);

        Optional<List<Number>> embedding = embeddingService.generateEmbedding(text, resolvedModel);
        if (embedding.isPresent()) {
            return ResponseEntity.ok(EmbeddingResponse.success(text, embedding.get()));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(EmbeddingResponse.failure(text, "Embedding service unavailable for model: " + resolvedModel));
    }

    // ── Health ────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health(
            @RequestParam(required = false) String model) {
        String resolvedModel = model != null ? model : vectorSearchService.getDefaultModel();
        boolean healthy = vectorSearchService.isEmbeddingServiceAvailable(resolvedModel);

        if (healthy) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "embeddingService", "UP",
                    "model", resolvedModel,
                    "database", "UP"
            ));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "status", "DEGRADED",
                        "embeddingService", "DOWN",
                        "model", resolvedModel,
                        "database", "UP",
                        "message", "Semantic search unavailable - embedding service is down"
                ));
    }
}
