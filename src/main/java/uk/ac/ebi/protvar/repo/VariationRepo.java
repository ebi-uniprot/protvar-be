package uk.ac.ebi.protvar.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
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
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.uniprot.domain.features.Feature;
import uk.ac.ebi.uniprot.domain.variation.Variant;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@AllArgsConstructor
public class VariationRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(VariationRepo.class);

    @Value("${tbl.variation}")
    private String variationTable;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public List<Feature> getFeatures(String accession, int proteinLocation) {
        List<Object[]> params = new ArrayList<>();
        params.add(new Object[] {accession, proteinLocation});
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
            return AminoAcid.fromOneLetter(letter).getThreeLetters();
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, List<Feature>> getFeatureMap(List<Object[]> accPosList) {
        if (accPosList == null || accPosList.isEmpty())
            return new HashedMap();
        String sql = String.format("SELECT * FROM %s WHERE (accession,position) in (:accPosList)",
                variationTable);
        SqlParameterSource parameters = new MapSqlParameterSource("accPosList", accPosList);
        return namedParameterJdbcTemplate.query(sql, parameters, new ResultSetExtractor<Map>() {
            @Override
            public Map extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<String, List<Feature>> featureMap = new HashMap();
                while(rs.next()){
                    String acc = rs.getString("accession");
                    int pos = rs.getInt("position");
                    Feature f = createVariant(rs);
                    if (f != null) {
                        String mapKey = acc + ":" + pos;
                        if (!featureMap.containsKey(mapKey))
                            featureMap.put(mapKey, new ArrayList<>());
                        featureMap.get(mapKey).add(f);
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