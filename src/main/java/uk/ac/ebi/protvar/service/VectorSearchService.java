package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.dto.VectorSearchResult;
import uk.ac.ebi.protvar.client.EmbeddingClient;
import uk.ac.ebi.protvar.repo.FunctionVectorRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingClient embeddingClient;
    private final FunctionVectorRepository vectorRepository;

    @Value("${embedding.model:biobert}")
    private String defaultModel;

    public List<VectorSearchResult> searchByText(String queryText, int limit, int offset, String model) {
        log.debug("Performing vector search for query: {}, model: {}", queryText, model);

        Optional<List<Number>> queryVector = embeddingClient.getEmbedding(queryText, model);

        if (queryVector.isEmpty()) {
            log.warn("Unable to get embedding for query text, returning empty results: {}", queryText);
            return Collections.emptyList();
        }

        return searchByVector(queryVector.get(), limit, offset, model);
    }

    public List<VectorSearchResult> searchByVector(List<Number> queryVector, int limit, int offset, String model) {
        try {
            return vectorRepository.findSimilarVectors(queryVector, limit, offset, model);
        } catch (Exception e) {
            log.error("Error performing vector search", e);
            return Collections.emptyList();
        }
    }

    public boolean isEmbeddingServiceAvailable() {
        return embeddingClient.isHealthy();
    }

    public boolean isEmbeddingServiceAvailable(String model) {
        return embeddingClient.isHealthy(model);
    }
}
