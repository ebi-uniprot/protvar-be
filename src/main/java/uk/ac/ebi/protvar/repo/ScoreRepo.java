package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.score.*;

import java.util.List;
import java.util.Objects;

/**
 * Conservation, EVE, ESM1b and AM scores
 */
@Repository
@AllArgsConstructor
public class ScoreRepo {

    private NamedParameterJdbcTemplate jdbcTemplate;

    private static final String WHERE = " WHERE accession=:acc AND position=:pos";

    private static final String CONSERV = "SELECT 'CONSERV' AS type, null AS mt_aa, score, null AS class FROM conserv_score" + WHERE;
    private static final String EVE = "SELECT 'EVE' AS type, mt_aa, score, class FROM eve_score" + WHERE;
    private static final String ESM = "SELECT 'ESM' AS type, mt_aa, score, NULL AS class FROM esm" + WHERE;
    private static final String AM = "SELECT 'AM' AS type, mt_aa, am_pathogenicity AS score, am_class AS class FROM alphamissense" + WHERE;

    // Scores that appear in functional annotation
    private static final String SCORES = """
        SELECT 'CONSERV' AS type, accession, position, null AS mt_aa, score, null AS class FROM conserv_score 
        INNER JOIN (VALUES :accPosList) AS t(acc,pos) ON t.acc=accession and t.pos=position
        UNION     		
        SELECT 'EVE' AS type, accession, position, mt_aa, score, class FROM eve_score 
        INNER JOIN (VALUES :accPosList) AS t(acc,pos) ON t.acc=accession and t.pos=position
        UNION 
        SELECT 'ESM' AS type, accession, position, mt_aa, score, null AS class FROM esm 
        INNER JOIN (VALUES :accPosList) AS t(acc,pos) ON t.acc=accession and t.pos=position
        UNION
        SELECT 'AM' AS type, accession, position, mt_aa, am_pathogenicity AS score, am_class AS class FROM alphamissense 
        INNER JOIN (VALUES :accPosList) AS t(acc,pos) ON t.acc=accession and t.pos=position
        """;

    // Used in PredictionController
    // Does not set the acc, pos and wt values in the Score object (to avoid transmitting
    // unneeded data) in the response.
    public List<Score> getScores(String acc, Integer pos, String mt, Score.Name name) {
        String sql = String.format("%s union %s union %s union %s", CONSERV, appendMt(EVE, mt), appendMt(ESM, mt), appendMt(AM, mt));
        if (name != null) {
            switch (name) {
                case CONSERV:
                    sql = CONSERV;
                    break;
                case EVE:
                    sql = appendMt(EVE, mt);
                    break;
                case ESM:
                    sql = appendMt(ESM, mt);
                    break;
                case AM:
                    sql = appendMt(AM, mt);
                    break;
            }
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource("acc", acc)
                .addValue("pos", pos)
                .addValue("mt", mt);
        List results = jdbcTemplate.query(sql,
                parameters,
                (rs, rowNum) -> {
                    String t = rs.getString("type");
                    if (t.equalsIgnoreCase(Score.Name.CONSERV.name())) {
                        return new ConservScore(null, rs.getDouble("score"));
                    } else if (t.equalsIgnoreCase(Score.Name.EVE.name())) {
                        return new EVEScore(rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
                    } else if (t.equalsIgnoreCase(Score.Name.ESM.name())) {
                        return new ESMScore(rs.getString("mt_aa"), rs.getDouble("score"));
                    } else if (t.equalsIgnoreCase(Score.Name.AM.name())) {
                        return new AMScore(rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
                    }
                    return null;
                });
        results.removeIf(Objects::isNull);
        return results;
    }

    // Used in MappingFetcher
    // This one needs to set the acc and pos (but not wt) to enable the use of groupBy
    // joinWithDash(name, acc, pos, mt)
    // in building the MappingResponse.
    public List<Score> getScores(List<Object[]> accPosList) {
        if (accPosList == null || accPosList.isEmpty()) return List.of();

        SqlParameterSource parameters = new MapSqlParameterSource("accPosList", accPosList);
        List results = jdbcTemplate.query(SCORES,
                parameters,
                (rs, rowNum) -> {
                    String t = rs.getString("type");
                    if (t.equalsIgnoreCase(Score.Name.CONSERV.name())) {
                        return new ConservScore(rs.getString("accession"), rs.getInt("position"), null, rs.getDouble("score"));
                    } else if (t.equalsIgnoreCase(Score.Name.EVE.name())) {
                        return new EVEScore(rs.getString("accession"), rs.getInt("position"), rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
                    } else if (t.equalsIgnoreCase(Score.Name.ESM.name())) {
                        return new ESMScore(rs.getString("accession"), rs.getInt("position"), rs.getString("mt_aa"), rs.getDouble("score"));
                    } else if (t.equalsIgnoreCase(Score.Name.AM.name())) {
                        return new AMScore(rs.getString("accession"), rs.getInt("position"), rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
                    }
                    return null;
                });
        results.removeIf(Objects::isNull);
        return results;
    }

    private String appendMt(String sql, String mt) {
        if (mt == null)
            return sql;
        return sql + " and mt_aa=:mt";
    }
}
