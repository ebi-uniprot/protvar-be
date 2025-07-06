package uk.ac.ebi.protvar.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
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
import java.util.*;

@Repository
@RequiredArgsConstructor
public class VariationRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariationRepo.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${tbl.variation}")
    private String variationTable;

    public List<Feature> getFeatures(String accession, int position) {
        List<Object[]> params = new ArrayList<>();
        params.add(new Object[] {accession, position});
        String sql = String.format("SELECT * FROM %s WHERE (accession,position) in (:accPosList)",
                variationTable);
        SqlParameterSource parameters = new MapSqlParameterSource("accPosList", params);


        return namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> createVariant(rs));
    }

    private Feature createVariant(ResultSet rs) throws SQLException {
        String variantsJson = rs.getString("features");
        try {
            Variant f = objectMapper.readValue(variantsJson, Variant.class);
            f.setWildType(toThreeLetterAminoAcid(f.getWildType()));
            f.setAlternativeSequence(toThreeLetterAminoAcid(f.getAlternativeSequence()));
            return f;
        }catch (JsonProcessingException ex) {
            LOGGER.error("Error mapping UniProt feature into domain model class: " + ex.getMessage());
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

    // Method for handling a list of accession-position pairs
    public Map<String, List<Feature>> getFeatureMap(String[] accessions, Integer[] positions) {
        if (accessions == null || accessions.length == 0)  return Map.of();

        String sql = String.format("""
        WITH coord_list (acc, pos) AS (
          SELECT * FROM unnest(:accessions::VARCHAR[], :positions::INT[])
        )
        SELECT * FROM %s
        INNER JOIN coord_list ON accession = coord_list.acc
          AND position = coord_list.pos
        """, variationTable);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accessions", accessions)
                .addValue("positions", positions);

        return getFeatureMapFromQuery(sql, parameters);
    }

    // Method for handling a single accession
    public Map<String, List<Feature>> getFeatureMap(String accession) {
        if (accession == null || accession.isEmpty())
            return new HashedMap();

        String sql = String.format("SELECT * FROM %s WHERE accession = :accession", variationTable);
        SqlParameterSource parameters = new MapSqlParameterSource("accession", accession);

        return getFeatureMapFromQuery(sql, parameters);
    }

    // Shared helper method for querying features from the database
    private Map<String, List<Feature>> getFeatureMapFromQuery(String sql, SqlParameterSource parameters) {
        return namedParameterJdbcTemplate.query(sql, parameters, new ResultSetExtractor<Map>() {
            @Override
            public Map extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<String, List<Feature>> featureMap = new HashMap();
                while (rs.next()) {
                    String acc = rs.getString("accession");
                    int pos = rs.getInt("position");
                    Feature f = createVariant(rs);
                    if (f != null) {
                        String variantKey = VariantKey.protein(acc, pos);
                        if (!featureMap.containsKey(variantKey))
                            featureMap.put(variantKey, new ArrayList<>());
                        featureMap.get(variantKey).add(f);
                    }
                }
                return featureMap;
            }
        });
    }

    private Feature createFeature(ResultSet rs) throws SQLException {
        String featuresJsonStr = rs.getString("features");
        try {
            Feature f = objectMapper.readValue(featuresJsonStr, Feature.class);
            return f;
        }catch (JsonProcessingException ex) {
            LOGGER.error("Error mapping UniProt feature into domain model class: " + ex.getMessage());
            return null;
        }
    }
}