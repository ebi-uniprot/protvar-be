package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Interaction;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class InteractionRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionRepo.class);

    private static final String SELECT_INTERACTIONS_BY_ACC_AND_RESID = """
		   SELECT a, a_residues, b, b_residues, pdockq
            FROM af2complexes_interaction 
            WHERE (a = :accession AND :resid = ANY(a_residues))
               OR (b = :accession AND :resid = ANY(b_residues))
		   """;
    private static final String SELECT_INTERACTIONS_BY_ACC_AND_RESID_NEW = """
		   SELECT a, ("a_residues_5A" || "a_residues_8A") as a_residues, 
		   b, ("b_residues_5A" || "b_residues_8A") as b_residues, pdockq 
		   FROM interaction_v2 
		   WHERE (a=:accession AND (:resid)=ANY("a_residues_5A" || "a_residues_8A")) 
		   OR (b=:accession AND (:resid)=ANY("b_residues_5A" || "b_residues_8A"))
		   """;
    private static final String SELECT_INTERACTION_MODEL = "SELECT pdb_model FROM af2complexes_interaction WHERE a=:a AND b=:b";
    private static final String SELECT_INTERACTION_MODEL_NEW = "SELECT pdb_model FROM interaction_v2 WHERE a=:a AND b=:b";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    // Query 1: Single accession + single residue
    public List<Interaction> getInteractions(String accession, Integer resid) {
        SqlParameterSource params = new MapSqlParameterSource("accession", accession)
                .addValue("resid", resid);
        return jdbcTemplate.query(SELECT_INTERACTIONS_BY_ACC_AND_RESID, params, (rs, rowNum) -> createInteraction(rs));
    }

    public Map<String, List<Interaction>> getInteractions(String accession, List<Integer> residues) {
        if (residues == null || residues.isEmpty()) return Collections.emptyMap();

        String sql = """
        SELECT a, a_residues, b, b_residues, pdockq,
            ARRAY(
                SELECT unnest(
                    CASE 
                        WHEN a = :accession THEN a_residues
                        ELSE b_residues 
                    END
                )
                INTERSECT
                SELECT unnest(:residues)
          ) AS matched_residues
        FROM af2complexes_interaction
        WHERE (a = :accession AND a_residues && :residues)
           OR (b = :accession AND b_residues && :residues)
        """;

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("residues", residues);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<Interaction>> result = new HashMap<>();

            while (rs.next()) {
                Interaction interaction = createInteraction(rs);
                Array matchedArray = rs.getArray("matched_residues");
                if (matchedArray == null) continue;

                Integer[] matchedResidues = (Integer[]) matchedArray.getArray();
                for (Integer resid : matchedResidues) {
                    String key = accession + "-" + resid;
                    result.computeIfAbsent(key, k -> new ArrayList<>()).add(interaction);
                }
            }

            //sortByScore(result);
            return result;
        });
    }

    // All residues for a given accession

    /**
     *
     * @param accession
     * @return accession-residue mapping
     */
    @Cacheable(value = "interactionsByAccession", key = "#accession")
    public Map<String, List<Interaction>> getInteractions(String accession) {
        String sql = """
            SELECT a, a_residues, b, b_residues, pdockq
            FROM af2complexes_interaction
            WHERE a = :accession OR b = :accession
            """;

        SqlParameterSource params = new MapSqlParameterSource("accession", accession);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<Interaction>> result = new HashMap<>();
            while (rs.next()) {
                Interaction interaction = createInteraction(rs);

                Array aResidues = rs.getArray("a_residues");
                Array bResidues = rs.getArray("b_residues");

                if (rs.getString("a").equals(accession) && aResidues != null) {
                    for (Integer resid : (Integer[]) aResidues.getArray()) {
                        String key = accession + "-" + resid;
                        result.computeIfAbsent(key, k -> new ArrayList<>()).add(interaction);
                    }
                }

                if (rs.getString("b").equals(accession) && bResidues != null) {
                    for (Integer resid : (Integer[]) bResidues.getArray()) {
                        String key = accession + "-" + resid;
                        result.computeIfAbsent(key, k -> new ArrayList<>()).add(interaction);
                    }
                }
            }
            return result;
        });
    }

    public String getInteractionModel(String a, String b) {
        SqlParameterSource parameters = new MapSqlParameterSource("a", a)
                .addValue("b", b);
        try {
            return jdbcTemplate.queryForObject(SELECT_INTERACTION_MODEL, parameters, (rs, rowNum) ->
                    rs.getString("pdb_model"));
        }
        catch (EmptyResultDataAccessException e) {
            LOGGER.warn("getInteractionModel returned empty result");
        }
        return null;
    }

    private Interaction createInteraction(ResultSet rs) throws SQLException {
        return new Interaction(
                rs.getString("a"),
                Arrays.asList((Integer[]) rs.getArray("a_residues").getArray()),
                rs.getString("b"),
                Arrays.asList((Integer[]) rs.getArray("b_residues").getArray()),
                rs.getDouble("pdockq")
        );
    }

    private <T> void sortByScore(Map<T, List<Interaction>> map) {
        map.values().forEach(list ->
                list.sort(Comparator.comparing(Interaction::getPdockq).reversed())
        );
    }
}
