package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.dto.VectorSearchResult;
import uk.ac.ebi.protvar.client.EmbeddingClient;
import uk.ac.ebi.protvar.repo.FunctionVectorRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service for semantic vector search operations.
 * Orchestrates embedding generation and vector similarity search.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingClient embeddingClient;
    private final FunctionVectorRepository vectorRepository;

    /**
     * Search for similar entries based on text query.
     * @param queryText Text to search for
     * @param limit Maximum number of results
     * @return List of similar results, empty if embedding service unavailable
     */
    public List<VectorSearchResult> searchByText(String queryText, int limit) {
        log.debug("Performing vector search for query: {}", queryText);

        Optional<float[]> queryVector = embeddingClient.getEmbedding(queryText);

        if (queryVector.isEmpty()) {
            log.warn("Unable to get embedding for query text, returning empty results: {}", queryText);
            return Collections.emptyList();
        }

        return searchByVector(queryVector.get(), limit);
    }

    /**
     * Search for similar entries using pre-computed embedding vector.
     * @param queryVector Embedding vector
     * @param limit Maximum number of results
     * @return List of similar results
     */
    public List<VectorSearchResult> searchByVector(float[] queryVector, int limit) {
        try {
            return vectorRepository.findSimilarVectors(queryVector, limit);
        } catch (Exception e) {
            log.error("Error performing vector search", e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if the embedding service is available.
     * @return true if service is healthy, false otherwise
     */
    public boolean isEmbeddingServiceAvailable() {
        return embeddingClient.isHealthy();
    }
}