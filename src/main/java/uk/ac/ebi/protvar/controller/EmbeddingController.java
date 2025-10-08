package uk.ac.ebi.protvar.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ebi.protvar.dto.EmbeddingRequest;
import uk.ac.ebi.protvar.dto.EmbeddingResponse;
import uk.ac.ebi.protvar.service.EmbeddingService;

import java.util.Map;
import java.util.Optional;

/**
 * REST controller for embedding generation endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/embedding")
@RequiredArgsConstructor
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    @GetMapping
    public ResponseEntity<EmbeddingResponse> getEmbeddingByGet(
            @RequestParam(name = "text") String text) {

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(EmbeddingResponse.failure(text, "Text parameter is required and cannot be empty"));
        }

        log.debug("GET request for embedding with text: {}", text);
        return generateEmbedding(text);
    }

    @PostMapping
    public ResponseEntity<EmbeddingResponse> getEmbeddingByPost(
            @RequestBody EmbeddingRequest request) {

        if (request == null || request.getText() == null || request.getText().trim().isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(EmbeddingResponse.failure(
                            request != null ? request.getText() : null,
                            "Text is required and cannot be empty"));
        }

        log.debug("POST request for embedding with text: {}", request.getText());
        return generateEmbedding(request.getText());
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> checkHealth() {
        boolean isHealthy = embeddingService.isServiceAvailable();
        if (isHealthy) {
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "service", "embedding-service"
            ));
        } else {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "status", "DOWN",
                            "service", "embedding-service"
                    ));
        }
    }

    private ResponseEntity<EmbeddingResponse> generateEmbedding(String text) {
        Optional<float[]> embedding = embeddingService.generateEmbedding(text);

        if (embedding.isPresent()) {
            return ResponseEntity.ok(EmbeddingResponse.success(text, embedding.get()));
        } else {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(EmbeddingResponse.failure(text, "Embedding service is currently unavailable"));
        }
    }
}