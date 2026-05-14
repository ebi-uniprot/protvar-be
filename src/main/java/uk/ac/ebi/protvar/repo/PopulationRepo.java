package uk.ac.ebi.protvar.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.types.AminoAcid;
import uk.ac.ebi.protvar.utils.VariantKey;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads variants from rel_{release}_population. raw_json holds the full
 * UniProt variation feature object — mapVariant just deserialises it into a
 * Variant and applies the 1→3-letter AA convention used by the FE.
 *
 * The structured columns on the table (wild_type, alt, consequence,
 * source_type, clin_sig) exist for SQL-side filtering by advanced search
 * (indexed on accession, (accession, position), source_type, consequence,
 * GIN clin_sig). They aren't read here — raw_json is the single source of
 * truth on the read path.
 */
@Repository
@RequiredArgsConstructor
public class PopulationRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(PopulationRepo.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String VARIANT_COLS = "accession, position, raw_json";

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${tbl.ann.pop}")
    private String populationTable;

    /** Variants at a single (accession, position). */
    public List<Feature> getFeatures(String accession, int position) {
        if (accession == null) return List.of();

        String sql = String.format("""
            SELECT %s FROM %s
            WHERE accession = :accession AND position = :position
            """, VARIANT_COLS, populationTable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("position", position);
        return namedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> mapVariant(rs));
    }

    /** Variants for many (accession, position) pairs, grouped by VariantKey. */
    public Map<String, List<Feature>> getFeatureMap(String[] accessions, Integer[] positions) {
        if (accessions == null || accessions.length == 0) return Map.of();

        String sql = String.format("""
            WITH coord_list (acc, pos) AS (
              SELECT * FROM unnest(:accessions::VARCHAR[], :positions::INT[])
            )
            SELECT %s FROM %s
            INNER JOIN coord_list ON accession = coord_list.acc
              AND position = coord_list.pos
            """, VARIANT_COLS, populationTable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accessions", accessions)
                .addValue("positions", positions);
        return queryFeatureMap(sql, params);
    }

    /** All variants for an accession, grouped by VariantKey. */
    public Map<String, List<Feature>> getFeatureMap(String accession) {
        if (accession == null || accession.isEmpty()) return Map.of();

        String sql = String.format("SELECT %s FROM %s WHERE accession = :accession",
                VARIANT_COLS, populationTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession);
        return queryFeatureMap(sql, params);
    }

    private Map<String, List<Feature>> queryFeatureMap(String sql, SqlParameterSource params) {
        return namedParameterJdbcTemplate.query(sql, params, new ResultSetExtractor<Map<String, List<Feature>>>() {
            @Override
            public Map<String, List<Feature>> extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<String, List<Feature>> featureMap = new HashMap<>();
                while (rs.next()) {
                    String acc = rs.getString("accession");
                    int pos = rs.getInt("position");
                    Feature v = mapVariant(rs);
                    if (v != null) {
                        String key = VariantKey.protein(acc, pos);
                        featureMap.computeIfAbsent(key, k -> new ArrayList<>()).add(v);
                    }
                }
                return featureMap;
            }
        });
    }

    private Feature mapVariant(ResultSet rs) throws SQLException {
        String rawJson = rs.getString("raw_json");
        if (rawJson == null) return null;
        try {
            Variant v = objectMapper.readValue(rawJson, Variant.class);
            v.setWildType(toThreeLetterAminoAcid(v.getWildType()));
            v.setAlternativeSequence(toThreeLetterAminoAcid(v.getAlternativeSequence()));
            return v;
        } catch (JsonProcessingException e) {
            LOGGER.error("Error mapping variant raw_json: {}", e.getMessage());
            return null;
        }
    }

    private String toThreeLetterAminoAcid(String letter) {
        try {
            return AminoAcid.fromOneLetter(letter).getThreeLetter();
        } catch (Exception e) {
            return null;
        }
    }
}
