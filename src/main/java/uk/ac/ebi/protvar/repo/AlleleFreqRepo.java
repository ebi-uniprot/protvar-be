package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AlleleFreqRepo {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${tbl.allelefreq}")
    private String allelefreqTable;

    private static final String ALLELE_FREQ_IN_CHR_POS = """
   			SELECT * FROM %s AS af
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos) 
   			ON t.chr=af.chr AND t.pos=af.pos
   			""";

    private static final String ALLELE_FREQ_WITH_UNNEST = """
    WITH coord_list (chr, pos) AS (
      SELECT UNNEST(:chromosomes) as chr, UNNEST(:positions) as pos
    )
    SELECT af.* FROM %s AS af
    JOIN coord_list ON af.chromosome = coord_list.chr
      AND af.position = coord_list.pos
    """;

    public List<AlleleFreq> getAlleleFreqs(List<Object[]> chrPosList) {
        if (chrPosList == null || chrPosList.isEmpty())
            return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);
        String sql = String.format(ALLELE_FREQ_IN_CHR_POS, allelefreqTable);
        return namedParameterJdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(AlleleFreq.class));
    }

    public List<AlleleFreq> getAlleleFreqs(String[] chromosomes, Integer[] positions) {
        if (chromosomes == null || chromosomes.length == 0)
            return List.of();

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("chromosomes", chromosomes)
                .addValue("positions", positions);
        String sql = String.format(ALLELE_FREQ_WITH_UNNEST, allelefreqTable);
        return namedParameterJdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(AlleleFreq.class));
    }

    public List<AlleleFreq> getAlleleFreq(String chr, Integer pos, String alt) {
        MapSqlParameterSource  parameters = new MapSqlParameterSource();
        parameters.addValue("chr", chr);
        parameters.addValue("pos", pos);
        String sql = String.format("""
				SELECT * FROM %s 
				WHERE chr=:chr 
				AND pos=:pos 
                """, allelefreqTable);
        if (alt != null && !alt.isEmpty()) {
            parameters.addValue("alt", alt);
            sql += " AND alt=:alt";
        }

        return namedParameterJdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(AlleleFreq.class));
    }
}