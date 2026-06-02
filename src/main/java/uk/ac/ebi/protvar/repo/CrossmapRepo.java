package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Crossmap;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class CrossmapRepo {
    private static final String CROSSMAPS_IN_GRCHX_POS = """
			SELECT * FROM %s AS cm 
			INNER JOIN (VALUES :positions) AS t(pos)
			ON t.pos=cm.grch%s_pos
			""";

    private static final String CROSSMAPS_IN_CHR_GRCH37_POS = """
   			SELECT * FROM %s AS cm 
   			INNER JOIN (VALUES :chrPos37) AS t(chr,pos)
   			ON t.chr=cm.chr AND t.pos=cm.grch37_pos
   			""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.crossmap}")
    private String crossmapTable;

    public double getPercentageMatch(List<Object[]> chrPosRefList, String ver) {
        String sql = String.format("""
    		SELECT 100 * COUNT (DISTINCT (chr, grchVER_pos, grchVER_base)) / :num 
    		FROM %s
    		WHERE (chr, grchVER_pos, grchVER_base) 
    		IN (:chrPosRef)
    		""", crossmapTable);

        sql = sql.replaceAll("VER", ver);

        SqlParameterSource parameters = new MapSqlParameterSource("num", chrPosRefList.size())
                .addValue("chrPosRef", chrPosRefList);

        return jdbcTemplate.queryForObject(sql, parameters, Integer.class);
    }

    public List<Crossmap> getCrossmaps(List<Object[]> positions, String grch) {
        if (positions.isEmpty())
            return new ArrayList<>();
        String sql = String.format(CROSSMAPS_IN_GRCHX_POS, crossmapTable, grch);
        SqlParameterSource parameters = new MapSqlParameterSource("positions", positions);
        return jdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(Crossmap.class));
    }

    public List<Crossmap> getCrossmapsByChrPos37(List<Object[]> chrPos37) {
        if (chrPos37.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("chrPos37", chrPos37);
        return jdbcTemplate.query(
                String.format(CROSSMAPS_IN_CHR_GRCH37_POS, crossmapTable),
                parameters,
                new BeanPropertyRowMapper<>(Crossmap.class));
    }

}
