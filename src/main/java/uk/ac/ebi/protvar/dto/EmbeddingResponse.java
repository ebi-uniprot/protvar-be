package uk.ac.ebi.protvar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {
    private List<Number> embedding;
    private Integer dimension;
    private String text;
    private boolean success;
    private String error;

    public static EmbeddingResponse success(String text, List<Number> embedding) {
        return new EmbeddingResponse(
                embedding,
                embedding.size(),
                text,
                true,
                null
        );
    }

    public static EmbeddingResponse failure(String text, String error) {
        return new EmbeddingResponse(
                null,
                null,
                text,
                false,
                error
        );
    }
}
