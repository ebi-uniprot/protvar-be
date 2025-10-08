package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.client.EmbeddingClient;

import java.util.Optional;

/**
 * Service for embedding generation operations.
 * Provides business logic layer for embedding functionality.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingClient embeddingClient;

    /**
     * Generate embedding for the given text.
     * @param text Input text
     * @return Optional containing embedding vector
     */
    public Optional<float[]> generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            log.warn("Attempted to generate embedding for empty text");
            return Optional.empty();
        }

        return embeddingClient.getEmbedding(text);
    }

    /**
     * Check if the embedding service is available.
     * @return true if service is healthy
     */
    public boolean isServiceAvailable() {
        return embeddingClient.isHealthy();
    }
}