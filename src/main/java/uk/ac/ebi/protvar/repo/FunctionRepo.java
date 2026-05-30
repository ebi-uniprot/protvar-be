package uk.ac.ebi.protvar.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;
import uk.ac.ebi.uniprot.domain.features.Evidence;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.uniprot.domain.features.FeatureCategory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reads function annotation data from the two-table layout produced by the
 * protvar-import-py pipeline:
 *
 *   rel_{R}_function           — one row per accession, header_json holds
 *                                      everything except features[].
 *   rel_{R}_function_feature   — one row per UniProt feature, with type,
 *                                      category, begin_pos, end_pos, raw_json.
 *
 * Header lookups are cached per-accession (replaces the old explicit preload).
 * Position-filtered feature queries are SQL-side (indexed on
 * (accession, begin_pos, end_pos)) — no in-memory filtering.
 */
@Repository
@RequiredArgsConstructor
public class FunctionRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tbl.ann.fun}")
    private String functionTable;

    @Value("${tbl.ann.fun.feature}")
    private String functionFeatureTable;

    /**
     * Per-protein header (everything except features[]). Result is cached;
     * `null` results are cached too so a missing accession isn't re-queried.
     */
    @Cacheable(value = "FUN_HEADER", key = "#accession")
    public UPEntry getHeader(String accession) {
        if (accession == null) return null;

        String sql = String.format("SELECT header_json FROM %s WHERE accession = :accession", functionTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession);
        try {
            String json = jdbcTemplate.queryForObject(sql, params, String.class);
            return objectMapper.readValue(json, UPEntry.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static final String FEATURE_COLS =
            "type, category, begin_pos, end_pos, description, evidences_json, raw_json";

    /** All features for an accession, ordered by UniProt's feature_index. */
    public List<Feature> getFeatures(String accession) {
        if (accession == null) return Collections.emptyList();

        String sql = String.format("""
            SELECT %s FROM %s
            WHERE accession = :accession
            ORDER BY feature_index
            """, FEATURE_COLS, functionFeatureTable);
        return jdbcTemplate.query(sql, new MapSqlParameterSource("accession", accession), this::mapFeature);
    }

    /**
     * Features at a residue position. SQL fetches by range
     * (position BETWEEN begin_pos AND end_pos); DISULFID is filtered down in
     * Java because its begin/end are the two linked cysteines, not a range —
     * only the endpoints themselves are "at" the position, not residues
     * between.
     */
    public List<Feature> getFeatures(String accession, int position) {
        if (accession == null) return Collections.emptyList();

        String sql = String.format("""
            SELECT %s FROM %s
            WHERE accession = :accession
              AND :position BETWEEN begin_pos AND end_pos
            ORDER BY feature_index
            """, FEATURE_COLS, functionFeatureTable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("position", position);
        List<Feature> features = jdbcTemplate.query(sql, params, this::mapFeature);

        String posStr = String.valueOf(position);
        return features.stream()
                .filter(f -> !"DISULFID".equals(f.getType())
                          || posStr.equals(f.getBegin())
                          || posStr.equals(f.getEnd()))
                .collect(Collectors.toList());
    }

    /**
     * Build a Feature from the structured columns merged with the raw_json
     * remnant. Columns are authoritative — we overwrite from cols regardless
     * of what raw_json carries, so this is robust to either importer shape
     * (raw_json with or without the extracted fields).
     */
    private Feature mapFeature(ResultSet rs, int rowNum) throws SQLException {
        Feature f;
        String rawJson = rs.getString("raw_json");
        if (rawJson != null) {
            try {
                f = objectMapper.readValue(rawJson, Feature.class);
            } catch (JsonProcessingException e) {
                f = new Feature();
            }
        } else {
            f = new Feature();
        }

        f.setType(rs.getString("type"));
        String category = rs.getString("category");
        if (category != null) {
            f.setCategory(FeatureCategory.valueOf(category));
        }
        Integer beginPos = rs.getObject("begin_pos", Integer.class);
        Integer endPos = rs.getObject("end_pos", Integer.class);
        if (beginPos != null) f.setBegin(String.valueOf(beginPos));
        if (endPos != null) f.setEnd(String.valueOf(endPos));
        f.setDescription(rs.getString("description"));

        String evidencesJson = rs.getString("evidences_json");
        if (evidencesJson != null) {
            try {
                List<Evidence> evidences = objectMapper.readValue(
                        evidencesJson, new TypeReference<List<Evidence>>() {});
                f.setEvidences(evidences);
            } catch (JsonProcessingException e) {
                // leave default empty evidences list
            }
        }
        return f;
    }
}
