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
    private static final String CONSERV = """
    		select 'CONSERV' as type, null as mt_aa, score, null as class
    		from conserv_score 
    		where acc=:acc and pos=:pos
 			""";
    private static final String EVE = """
			select 'EVE' as type, mt_aa, score, class 
			from eve_score 
			where accession=:acc and position=:pos
			""";
    private static final String ESM = """
			select 'ESM' as type, mt_aa, score, null as class 
			from esm 
			where accession=:acc and position=:pos
			""";
    private static final String AM = """
			select 'AM' as type, mt_aa, am_pathogenicity as score, am_class as class 
			from alphamissense 
			where accession=:acc and position=:pos
			""";

    private static final String SCORES = """
    		select 'CONSERV' as type, acc as accession, pos as position, null as mt_aa, score, null as class
    		from conserv_score 
    		inner join (values :accPosList) as t(_acc,_pos)
    		on t._acc=acc and t._pos=pos
			union    		
         	select 'EVE' as type, accession, position, mt_aa, score, class 
			from eve_score 
			inner join (values :accPosList) as t(_acc,_pos)
			on t._acc=accession and t._pos=position
			union
			select 'ESM' as type, accession, position, mt_aa, score, null as class 
			from esm 
    		inner join (values :accPosList) as t(_acc,_pos)
			on t._acc=accession and t._pos=position
			union
			select 'AM' as type, accession, position, mt_aa, am_pathogenicity as score, am_class as class 
			from alphamissense 
    		inner join (values :accPosList) as t(_acc,_pos)
			on t._acc=accession and t._pos=position
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
        if (accPosList.size() > 0) {
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
        return List.of();
    }

    private String appendMt(String sql, String mt) {
        if (mt == null)
            return sql;
        return sql + " and mt_aa=:mt";
    }
}
