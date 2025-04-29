package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.score.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservation, EVE, ESM1b and AM scores
 */
@Repository
@RequiredArgsConstructor
public class ScoreRepo {

    private static final String SELECT_CONSERV = "SELECT 'CONSERV' AS type, accession, position, null AS mt_aa, score, null AS class FROM conserv_score";
    private static final String SELECT_EVE = "SELECT 'EVE' AS type, accession, position, mt_aa, score, class FROM eve_score";
    private static final String SELECT_ESM = "SELECT 'ESM' AS type, accession, position, mt_aa, score, null AS class FROM esm";
    private static final String SELECT_AM = "SELECT 'AM' AS type, accession, position, mt_aa, am_pathogenicity AS score, am_class AS class FROM alphamissense";

    private static final String JOIN_ON_ACC_POS = " INNER JOIN input ON input.acc = accession AND input.pos = position";
    private static final String WHERE_ACC = " WHERE accession=:accession";
    private static final String WHERE_ACC_POS = WHERE_ACC + " AND position=:position";
    private static final String WHERE_ACC_POS_MT = WHERE_ACC_POS + " AND mt_aa=:mutatedType";

    // Scores that appear in functional annotation
    // Optimized SQL query using unnest instead of VALUES
    private static final String SELECT_SCORES_FOR_ACC_POS = """
    WITH input(acc, pos) AS (
        SELECT unnest(:accessions::VARCHAR[]), unnest(:positions::INT[])
    )
    """ + String.join(" UNION ALL ",
            SELECT_CONSERV + JOIN_ON_ACC_POS,
            SELECT_EVE + JOIN_ON_ACC_POS,
            SELECT_ESM + JOIN_ON_ACC_POS,
            SELECT_AM + JOIN_ON_ACC_POS
    );

    private static final String SELECT_SCORES_FOR_ACC = String.join(" UNION ALL ",
            SELECT_CONSERV + WHERE_ACC,
            SELECT_EVE + WHERE_ACC,
            SELECT_ESM + WHERE_ACC,
            SELECT_AM + WHERE_ACC
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Used in PredictionController
     * Returns scores for a given accession, position, and optionally a mutation, limited to a specific score type.
     * Does not populate accession or position fields in the Score object (to avoid transmitting unneeded data in the
     * response)
     */
    public List<Score> getScores(String accession, Integer position, String mutatedType, Score.Name name) {
        String where = (mutatedType != null && !mutatedType.isBlank()) ? WHERE_ACC_POS_MT : WHERE_ACC_POS;
        String sql = name == null ? (
                String.join(" UNION ALL ",
                        SELECT_CONSERV + WHERE_ACC_POS, // conserv_score table has no mt field
                        SELECT_EVE + where,
                        SELECT_ESM + where,
                        SELECT_AM + where
                )
        ) : (switch (name) {
            case CONSERV -> SELECT_CONSERV + WHERE_ACC_POS;
            case EVE -> SELECT_EVE + where;
            case ESM -> SELECT_ESM + where;
            case AM -> SELECT_AM + where;
        });

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("position", position)
                .addValue("mutatedType", mutatedType);  // Safe even if null, won't be used unless required

        return jdbcTemplate.query(sql, parameters, scoreRowMapperWithoutAccPos);
    }

    private String buildScoreQuery(Score.Name name, boolean filterByMt) {
        String where = filterByMt ? WHERE_ACC_POS_MT : WHERE_ACC_POS;
        return switch (name) {
            case CONSERV -> SELECT_CONSERV + where;
            case EVE     -> SELECT_EVE + where;
            case ESM     -> SELECT_ESM + where;
            case AM      -> SELECT_AM + where;
            default -> String.join(" UNION ALL ",
                    SELECT_CONSERV + where,
                    SELECT_EVE + where,
                    SELECT_ESM + where,
                    SELECT_AM + where
            );
        };
    }

    /**
     * Used in MappingFetcher
     * Returns all scores for a list of (accession, position) pairs.
     * Need to set the acc and pos (but not wt) to enable the use of groupBy in building the MappingResponse.
     */
    public List<Score> getScores(List<Object[]> accPosList) {
        if (accPosList == null || accPosList.isEmpty()) return List.of();

        //SqlParameterSource parameters = new MapSqlParameterSource("accPosList", accPosList);
        // Optimized SQL query using unnest instead of VALUES
        List<String> accessions = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (Object[] pair : accPosList) {
            accessions.add((String) pair[0]);
            positions.add((Integer) pair[1]);
        }

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accessions", accessions.toArray(new String[0]))  // Convert to array
                .addValue("positions", positions.toArray(new Integer[0]));  // Convert to array

        return jdbcTemplate.query(SELECT_SCORES_FOR_ACC_POS, parameters, scoreRowMapper);
    }

    /**
     * Returns all scores for a given accession.
     */

    @Cacheable(value = "scoresByAccession", key = "#accession")
    public List<Score> getScores(String accession) {
        if (accession == null || accession.isBlank()) return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("accession", accession);
        return jdbcTemplate.query(SELECT_SCORES_FOR_ACC, parameters, scoreRowMapper);
    }

    /**
     * Returns all AM scores for a given accession.
     */
    public List<Score> getAMScores(String accession) {
        if (accession == null || accession.isBlank()) return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("accession", accession);
        return jdbcTemplate.query(SELECT_AM + WHERE_ACC, parameters, scoreRowMapper);
    }

    private final RowMapper<Score> scoreRowMapper = (rs, rowNum) -> {
        Score.Name name = Score.Name.valueOf(rs.getString("type"));
        String acc = rs.getString("accession");
        int pos = rs.getInt("position");
        String mt = rs.getString("mt_aa");
        double score = rs.getDouble("score");
        Integer clazz = rs.getObject("class", Integer.class);

        return switch (name) {
            case CONSERV -> new ConservScore(acc, pos, null, score);
            case EVE     -> new EVEScore(acc, pos, mt, score, clazz);
            case ESM     -> new ESMScore(acc, pos, mt, score);
            case AM      -> new AMScore(acc, pos, mt, score, clazz);
        };
    };

    private final RowMapper<Score> scoreRowMapperWithoutAccPos = (rs, rowNum) -> {
        Score.Name name = Score.Name.valueOf(rs.getString("type"));
        String mt = rs.getString("mt_aa");
        double score = rs.getDouble("score");
        Integer clazz = rs.getObject("class", Integer.class);

        return switch (name) {
            case CONSERV -> new ConservScore(null, score);
            case EVE     -> new EVEScore(mt, score, clazz);
            case ESM     -> new ESMScore(mt, score);
            case AM      -> new AMScore(mt, score, clazz);
        };
    };
}
