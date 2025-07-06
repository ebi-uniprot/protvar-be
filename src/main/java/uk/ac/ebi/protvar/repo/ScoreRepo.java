package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.EveClass;

import java.util.*;

/**
 * Conservation, EVE, ESM1b and AM scores
 */
@Repository
@RequiredArgsConstructor
public class ScoreRepo {
    private static final String CONSERV_SCORES =
            "SELECT 'CONSERV' AS type, s.accession, s.position, null AS mt_aa, s.score, null AS class FROM conserv_score s";

    private static final String EVE_SCORES =
            "SELECT 'EVE' AS type, s.accession, s.position, s.mt_aa, s.score, s.class FROM eve_score s";

    private static final String ESM_SCORES =
            "SELECT 'ESM' AS type, s.accession, s.position, s.mt_aa, s.score, null AS class FROM esm s";

    private static final String AM_SCORES =
            "SELECT 'AM' AS type, s.accession, s.position, s.mt_aa, s.am_pathogenicity AS score, s.am_class AS class FROM alphamissense s";
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Used in PredictionController
     * Returns scores for a given accession, position, and optionally a mutation, limited to a specific score type.
     * Does not populate accession or position fields in the Score object (to avoid transmitting unneeded data in the
     * response)
     */

    public List<Score> getScores(String accession, Integer position, String mutatedType, ScoreType type) {
        if (accession == null || position == null || type == null) {
            return Collections.emptyList();
        }
        List<ScoreType> typesToQuery = (type != null)
                ? List.of(type)
                : List.of(ScoreType.values());

        List<String> queries = new ArrayList<>();

        // Base WHERE clause
        String baseFilter = " WHERE accession = :accession AND position = :position";

        // Optional mt_aa filter for types that support it
        String extendedFilter = baseFilter;
        if (mutatedType != null && !mutatedType.isBlank()) {
            extendedFilter += " AND mt_aa = :mutatedType";
        }

        // Dynamically inject WHERE clause into each subquery
        if (typesToQuery.contains(ScoreType.CONSERV)) queries.add(CONSERV_SCORES + baseFilter); // no mutated type
        if (typesToQuery.contains(ScoreType.EVE)) queries.add(EVE_SCORES + extendedFilter);
        if (typesToQuery.contains(ScoreType.ESM)) queries.add(ESM_SCORES + extendedFilter);
        if (typesToQuery.contains(ScoreType.AM)) queries.add(AM_SCORES + extendedFilter);

        if (queries.isEmpty()) return Collections.emptyList();

        String sql = String.join(" UNION ALL ", queries);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("position", position)
                .addValue("mutatedType", mutatedType);  // Safe even if null, won't be used unless required

        return jdbcTemplate.query(sql, params, minimalScoreRowMapper);
    }

    /**
     * Returns all scores for a given accession.
     */
    @Cacheable(value = "scoresByAccession", key = "#accession")
    public List<Score> getScores(String accession) {// Set<ScoreType> types) {
        if (accession == null || accession.isBlank()) return List.of();

        // If types is null or empty, query all ScoreTypes
        Set<ScoreType> typesToQuery = //(types == null || types.isEmpty()) ?
                EnumSet.allOf(ScoreType.class);
                //: types;

        List<String> queries = new ArrayList<>();
        String whereClause = " WHERE s.accession = :accession ";

        if (typesToQuery.contains(ScoreType.CONSERV)) queries.add(CONSERV_SCORES + whereClause);
        if (typesToQuery.contains(ScoreType.EVE)) queries.add(EVE_SCORES + whereClause);
        if (typesToQuery.contains(ScoreType.ESM)) queries.add(ESM_SCORES + whereClause);
        if (typesToQuery.contains(ScoreType.AM)) queries.add(AM_SCORES + whereClause);

        if (queries.isEmpty()) return Collections.emptyList();

        // Join all with UNION ALL
        String sql = String.join(" UNION ALL ", queries);

        SqlParameterSource params = new MapSqlParameterSource("accession", accession);
        return jdbcTemplate.query(sql, params, fullScoreRowMapper);
    }

    /**
     * Returns all scores for array of accession-position pairs.
     * Need to set the acc and pos (but not wt) to enable the use of groupBy in building the MappingResponse.
     */
    public List<Score> getScores(String[] accessions, Integer[] positions, Set<ScoreType> types) {
        if (accessions == null || accessions.length == 0) return Collections.emptyList();

        // If types is null or empty, query all ScoreTypes
        Set<ScoreType> typesToQuery = (types == null || types.isEmpty())
                ? EnumSet.allOf(ScoreType.class)
                : types;

        List<String> queries = new ArrayList<>();

        // With and join clauses
        String withClause = """
                WITH input(acc, pos) AS (
                  SELECT * FROM unnest(:accessions::VARCHAR[], :positions::INT[])
                )
                """;
        //SELECT unnest(ARRAY['A', 'B']), unnest(ARRAY[1, 2]);
        // Results in:
        // | unnest | unnest |
        // | ------ | ------ |
        // | A      | 1      |
        // | A      | 2      |
        // | B      | 1      |
        // | B      | 2      |
        // vs.
        // SELECT * FROM unnest(ARRAY['A', 'B'], ARRAY[1, 2]); << THIS IS WHAT WE NEED!!
        // Results in:
        // | column1 | column2 |
        // | ------- | ------- |
        // | A       | 1       |
        // | B       | 2       |

        // Join clause used in all subqueries
        String joinClause = " JOIN input ON input.acc = s.accession AND input.pos = s.position ";

        // For score types that don't have mt_aa (CONSERV)
        if (typesToQuery.contains(ScoreType.CONSERV)) queries.add(CONSERV_SCORES + joinClause);

        // For others with mt_aa, no mutatedType filter here because we don't have that param
        if (typesToQuery.contains(ScoreType.EVE)) queries.add(EVE_SCORES + joinClause);
        if (typesToQuery.contains(ScoreType.ESM)) queries.add(ESM_SCORES + joinClause);
        if (typesToQuery.contains(ScoreType.AM)) queries.add(AM_SCORES + joinClause);

        if (queries.isEmpty()) return Collections.emptyList();

        // Join all with UNION ALL
        String sql = withClause + String.join(" UNION ALL ", queries);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accessions", accessions)
                .addValue("positions", positions);

        return jdbcTemplate.query(sql, params, fullScoreRowMapper);
    }

    public List<Score> getMappingScores(String[] accessions, Integer[] positions) {
        return getScores(accessions, positions, Set.of(ScoreType.AM));
    }

    public List<Score> getAnnotationScores(String[] accessions, Integer[] positions) {
        return getScores(accessions, positions, Set.of(ScoreType.CONSERV, ScoreType.EVE, ScoreType.ESM));
    }

    private final RowMapper<Score> fullScoreRowMapper = (rs, rowNum) -> {
        ScoreType type = ScoreType.valueOf(rs.getString("type"));
        String acc = rs.getString("accession");
        int pos = rs.getInt("position");
        String mt = rs.getString("mt_aa");
        double score = rs.getDouble("score");
        Integer clazz = rs.getObject("class", Integer.class);

        return switch (type) {
            case CONSERV -> new ConservScore(acc, pos, score);
            case EVE     -> new EveScore(acc, pos, mt, score, EveClass.fromValue(clazz));
            case ESM     -> new EsmScore(acc, pos, mt, score);
            case AM      -> new AmScore(acc, pos, mt, score, AmClass.fromValue(clazz));
        };
    };

    private final RowMapper<Score> minimalScoreRowMapper = (rs, rowNum) -> {
        ScoreType name = ScoreType.valueOf(rs.getString("type"));
        String mt = rs.getString("mt_aa");
        double score = rs.getDouble("score");
        Integer clazz = rs.getObject("class", Integer.class);

        return switch (name) {
            case CONSERV -> new ConservScore(score);
            case EVE     -> new EveScore(mt, score, EveClass.fromValue(clazz));
            case ESM     -> new EsmScore(mt, score);
            case AM      -> new AmScore(mt, score, AmClass.fromValue(clazz));
        };
    };

}
