package uk.ac.ebi.protvar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Semantic-search response — side-by-side hits from both corpora. Cosine
 * scores are not comparable across corpora, so the two lists are returned
 * separately (no merged ranking) for the FE to render as two sections.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResponse {
    private String query;
    private List<VectorSearchResult> functionResults;
    private List<PopulationVectorSearchResult> populationResults;
    private Integer limit;
    private Integer offset;
    private String model;
    private boolean success;
    private String error;

    public static VectorSearchResponse success(String query,
                                               List<VectorSearchResult> functionResults,
                                               List<PopulationVectorSearchResult> populationResults,
                                               int limit, int offset, String model) {
        return new VectorSearchResponse(
                query, functionResults, populationResults, limit, offset, model, true, null);
    }

    public static VectorSearchResponse failure(String error) {
        return new VectorSearchResponse(
                null, null, null, null, null, null, false, error);
    }
}
