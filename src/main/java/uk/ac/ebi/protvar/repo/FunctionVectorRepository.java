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

    @Value("${vector.table.prefix:rel_2025_01_function_vector_}")
    private String tableNamePrefix;

    @Value("${vector.text.table:rel_2025_01_function_text}")
    private String functionTextTable;

    public List<VectorSearchResult> findSimilarVectors(List<Number> queryVector, int limit, int offset, String model) {
        String embeddingTable = tableNamePrefix + model;
        String sql = String.format("""
            SELECT
                ft.accession, ft.source_type, ft.source_text,
                ft.begin_pos, ft.end_pos,
                (fv.embedding <=> :queryVector::vector) AS distance
            FROM %s fv
            JOIN %s ft ON ft.id = fv.text_id
            ORDER BY distance ASC
            LIMIT :limit OFFSET :offset
            """, embeddingTable, functionTextTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryVector", new PGvector(queryVector))
                .addValue("limit", limit)
                .addValue("offset", offset);

        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new VectorSearchResult(
                        rs.getString("accession"),
                        rs.getString("source_type"),
                        rs.getString("source_text"),
                        rs.getDouble("distance"),
                        (Integer) rs.getObject("begin_pos"),
                        (Integer) rs.getObject("end_pos")
                )
        );
    }
}
