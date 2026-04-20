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
    private Integer limit;
    private Integer offset;
    private String model;
    private boolean success;
    private String error;

    public static VectorSearchResponse success(String query, List<VectorSearchResult> results, int limit, int offset, String model) {
        return new VectorSearchResponse(
                query,
                results,
                limit,
                offset,
                model,
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
                null,
                null,
                false,
                error
        );
    }
}
