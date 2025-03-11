package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.AlleleFreq;
import java.util.List;

@Repository
@AllArgsConstructor
public class AlleleFreqRepo {
    @Value("${tbl.allelefreq}")
    private String allelefreqTable;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private static final String ALLELE_FREQ_IN_CHR_POS = """
   			SELECT * FROM %s AS af
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos) 
   			ON t.chr=af.chr AND t.pos=af.pos
   			""";

    public List<AlleleFreq> getAlleleFreqs(List<Object[]> chrPosList) {
        if (chrPosList == null || chrPosList.isEmpty())
            return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);
        String sql = String.format(ALLELE_FREQ_IN_CHR_POS, allelefreqTable);
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