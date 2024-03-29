package uk.ac.ebi.protvar.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.uniprot.variation.model.Feature;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@AllArgsConstructor
public class VariationRepo {

    private static final Logger LOGGER = LoggerFactory.getLogger(VariationRepo.class);

    public static final String SELECT_VARIATION_WHERE_ACC_AND_POS_IN =
            "SELECT * FROM variation WHERE (accession,position) in (:accPosSet)";

    private static final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<Feature> getFeatures(String accession, int proteinLocation) {
        Set<Object[]> params = new HashSet<>();
        params.add(new Object[] {accession, proteinLocation});
        SqlParameterSource parameters = new MapSqlParameterSource("accPosSet", params);
        return namedParameterJdbcTemplate.query(SELECT_VARIATION_WHERE_ACC_AND_POS_IN, parameters, (rs, rowNum) -> createFeature(rs));
    }

    public Map<String, List<Feature>> getFeatureMap(Set<Object[]> accPosSet) {
        if (accPosSet == null || accPosSet.isEmpty())
            return new HashedMap();

        SqlParameterSource parameters = new MapSqlParameterSource("accPosSet", accPosSet);
        return namedParameterJdbcTemplate.query(SELECT_VARIATION_WHERE_ACC_AND_POS_IN, parameters, new ResultSetExtractor<Map>() {
            @Override
            public Map extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<String, List<Feature>> featureMap = new HashMap();
                while(rs.next()){
                    String acc = rs.getString("accession");
                    int pos = rs.getInt("position");
                    Feature f = createFeature(rs);
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