package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.CaddPrediction;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CaddPredictionRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.cadd}")
    private String caddTable;

    private static final String CADDS_IN_CHR_POS = """
   			SELECT * FROM %s 
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos) 
   			ON t.chr=chromosome AND t.pos=position
   			"""; // optimised from: SELECT * FROM <tbl.cadd> WHERE (chromosome,position) IN (:chrPosList)
    // to avoid max num of "in" values reached. Refer to https://stackoverflow.com/questions/1009706/


    public List<CaddPrediction> getCADDByChrPos(List<Object[]> chrPosList) {
        if (chrPosList == null || chrPosList.isEmpty())
            return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);
        String sql = String.format(CADDS_IN_CHR_POS, caddTable);
        return jdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(CaddPrediction.class));
    }

}
