package uk.ac.ebi.protvar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VectorSearchResponse {
    private String query;
    private List<VectorSearchResult> results;
    private Integer count;
    private Integer limit;
    private boolean success;
    private String error;

    public static VectorSearchResponse success(String query, List<VectorSearchResult> results, int limit) {
        return new VectorSearchResponse(
                query,
                results,
                results.size(),
                limit,
                true,
                null
        );
    }

    public static VectorSearchResponse failure(String error) {
        return new VectorSearchResponse(
                null,
                null,
                0,
                null,
                false,
                error
        );
    }
}
