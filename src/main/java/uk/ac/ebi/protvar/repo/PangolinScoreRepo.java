package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.PangolinScore;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PangolinScoreRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.pangolin}")
    private String pangolinTable;

    // same shape as GenomicScoreRepo; (chromosome, position) is the prefix of the table's key index
    private static final String WITH_UNNEST = """
    WITH coord_list (chr, pos) AS (
      SELECT UNNEST(:chromosomes) as chr, UNNEST(:positions) as pos
    )
    SELECT * FROM %s
    JOIN coord_list ON chromosome = coord_list.chr
      AND position = coord_list.pos
    """;

    public List<PangolinScore> getByChrPos(String[] chromosomes, Integer[] positions) {
        if (chromosomes == null || chromosomes.length == 0)
            return List.of();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("chromosomes", chromosomes)
                .addValue("positions", positions);
        return jdbcTemplate.query(String.format(WITH_UNNEST, pangolinTable), parameters,
                new BeanPropertyRowMapper<>(PangolinScore.class));
    }
}
