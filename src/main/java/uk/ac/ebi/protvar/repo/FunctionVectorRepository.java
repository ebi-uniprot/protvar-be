package uk.ac.ebi.protvar.repo;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.dto.VectorSearchResult;

import java.util.List;

/**
 * Repository for vector similarity search operations.
 * Responsible only for database queries using pgvector.
 */
@Repository
@RequiredArgsConstructor
public class FunctionVectorRepository {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${vector.table.name:rel_2025_01_function_vector}")
    private String tableName;

    /**
     * Find vectors similar to the query vector using cosine distance.
     * @param queryVector Query embedding vector
     * @param limit Maximum number of results
     * @return List of similar vectors with metadata
     */
    public List<VectorSearchResult> findSimilarVectors(float[] queryVector, int limit) {
        String sql = String.format("""
            SELECT
                accession, source_type, source_text,
                (embedding <=> :queryVector::vector) AS distance
                -- Get similarity score (1 - distance = similarity)
                --1 - (embedding <=> :queryVector::vector) AS similarity
                -- Additional useful context:
                --array_length(embedding::float8[], 1) AS embedding_dimension,
                --char_length(source_text) AS text_length
            FROM
                %s
            ORDER BY
                distance ASC
            LIMIT :limit
            """, tableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryVector", new PGvector(queryVector))
                .addValue("limit", limit);

        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new VectorSearchResult(
                        rs.getString("accession"),
                        rs.getString("source_type"),
                        rs.getString("source_text"),
                        rs.getFloat("distance")
                )
        );
    }
}
