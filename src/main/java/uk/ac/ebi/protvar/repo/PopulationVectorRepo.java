package uk.ac.ebi.protvar.repo;

import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.dto.PopulationVectorSearchResult;

import java.util.List;

/**
 * Vector similarity search over the population corpus — mirror of
 * {@link FunctionVectorRepo}. Joins the per-model embedding table to
 * population_text for the source-text snippet.
 */
@Repository
@RequiredArgsConstructor
public class PopulationVectorRepo {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    /**
     * Base population annotation table, e.g. {@code rel_{R}_population}. The
     * per-model embedding table ({@code _vector_<model>}) and the text table
     * ({@code _text}) derive from it — those suffixes are a fixed import-pipeline
     * naming convention, so they live in code, not config.
     */
    @Value("${tbl.ann.pop}")
    private String populationTable;

    public List<PopulationVectorSearchResult> findSimilarVectors(List<Number> queryVector, int limit, int offset, String model) {
        String embeddingTable = populationTable + "_vector_" + model;
        String textTable = populationTable + "_text";
        String sql = String.format("""
            SELECT
                pt.accession, pt.position, pt.source_text,
                (pv.embedding <=> :queryVector::vector) AS distance
            FROM %s pv
            JOIN %s pt ON pt.id = pv.text_id
            ORDER BY distance ASC
            LIMIT :limit OFFSET :offset
            """, embeddingTable, textTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("queryVector", new PGvector(queryVector))
                .addValue("limit", limit)
                .addValue("offset", offset);

        return namedParameterJdbcTemplate.query(
                sql,
                params,
                (rs, rowNum) -> new PopulationVectorSearchResult(
                        rs.getString("accession"),
                        (Integer) rs.getObject("position"),
                        rs.getString("source_text"),
                        rs.getDouble("distance")
                )
        );
    }
}
