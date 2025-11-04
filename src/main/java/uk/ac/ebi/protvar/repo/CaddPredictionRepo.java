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
    /*
    optimised from:
        SELECT * FROM %s
        WHERE (chromosome,position) IN (:chrPosList);
    to avoid max num of "IN" values reached.
    Refer to https://stackoverflow.com/questions/1009706/ for details.

    changed from:
        SELECT * FROM %s
   		INNER JOIN (VALUES :chrPosList) AS t(chr,pos)
   		ON t.chr=chromosome AND t.pos=position
   	to using WITH.

   	and to using unnest
     */

    /* TODO check
    select * from <tbl.cadd> where chromosome='21' and position=26170620;
    This should return 3 rows but returning 9!
    Duplicates! using distinct fixes it but issue needs to be
    addressed at source (import) and chr-pos-allele-alt need
    to be all PKs (no need to save allele in tbl).
     */
    private static final String CADDS_WITH_COORD_LIST = """
        WITH coord_list (chr, pos) AS (
          VALUES :chrPosList
        )
        SELECT * FROM %s
        JOIN coord_list ON chromosome = coord_list.chr
          AND position = coord_list.pos
        """;

    public List<CaddPrediction> getCADDByChrPos(List<Object[]> chrPosList) {
        if (chrPosList == null || chrPosList.isEmpty())
            return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);
        String sql = String.format(CADDS_WITH_COORD_LIST, caddTable);
        return jdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(CaddPrediction.class));
    }

    // Needs positional accuracy
    private static final String CADDS_WITH_UNNEST = """
    WITH coord_list (chr, pos) AS (
      SELECT UNNEST(:chromosomes) as chr, UNNEST(:positions) as pos
    )
    SELECT * FROM %s
    JOIN coord_list ON chromosome = coord_list.chr
      AND position = coord_list.pos
    """;

    public List<CaddPrediction> getCADDByChrPos(String[] chromosomes, Integer[] positions) {
        if (chromosomes == null || chromosomes.length == 0)
            return List.of();

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("chromosomes", chromosomes)
                .addValue("positions", positions);

        String sql = String.format(CADDS_WITH_UNNEST, caddTable);
        return jdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(CaddPrediction.class));
    }
}
