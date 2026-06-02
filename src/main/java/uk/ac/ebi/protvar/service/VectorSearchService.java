package uk.ac.ebi.protvar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.ac.ebi.protvar.config.ModelRegistryProperties;
import uk.ac.ebi.protvar.dto.PopulationVectorSearchResult;
import uk.ac.ebi.protvar.dto.VectorSearchResult;
import uk.ac.ebi.protvar.client.EmbeddingClient;
import uk.ac.ebi.protvar.repo.FunctionVectorRepo;
import uk.ac.ebi.protvar.repo.PopulationVectorRepo;

import java.util.Collections;
import java.util.List;

/**
 * Vector similarity search over the two corpora — function and population.
 *
 * The semantic-search endpoint embeds the query text once (see
 * SemanticSearchController) and passes the vector to both
 * {@link #searchByVector} (function) and {@link #searchPopulationByVector}
 * (population) — avoiding a second round-trip to the embedding service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchService {

    private final EmbeddingClient embeddingClient;
    private final FunctionVectorRepo functionVectorRepo;
    private final PopulationVectorRepo populationVectorRepo;
    private final ModelRegistryProperties modelRegistry;

    /** Function-corpus kNN. */
    public List<VectorSearchResult> searchByVector(List<Number> queryVector, int limit, int offset, String model) {
        try {
            return functionVectorRepo.findSimilarVectors(queryVector, limit, offset, model);
        } catch (Exception e) {
            log.error("Error performing function vector search", e);
            return Collections.emptyList();
        }
    }

    /** Population-corpus kNN. */
    public List<PopulationVectorSearchResult> searchPopulationByVector(List<Number> queryVector, int limit, int offset, String model) {
        try {
            return populationVectorRepo.findSimilarVectors(queryVector, limit, offset, model);
        } catch (Exception e) {
            log.error("Error performing population vector search", e);
            return Collections.emptyList();
        }
    }

    public String getDefaultModel() {
        return modelRegistry.getDefaultModel();
    }

    public boolean isEmbeddingServiceAvailable() {
        return embeddingClient.isHealthy();
    }

    public boolean isEmbeddingServiceAvailable(String model) {
        return embeddingClient.isHealthy(model);
    }
}
