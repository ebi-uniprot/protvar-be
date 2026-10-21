package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.response.Richness;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RichnessRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(RichnessRepo.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.richness}")
    private String richnessTable;

    public static String key(String accession, int position) {
        return accession + ":" + position;
    }

    // Richness per (accession, position), keyed by key(). Empty if the table is missing: richness is optional.
    public Map<String, Richness> getRichness(String[] accs, Integer[] positions) {
        Map<String, Richness> map = new HashMap<>();
        if (accs.length == 0) return map;
        String sql = """
            SELECT r.accession, r.position, r.fun, r.pop, r.str
            FROM unnest(:accs::VARCHAR[], :positions::INT[]) AS i(acc, pos)
            JOIN %s r ON r.accession = i.acc AND r.position = i.pos
            """.formatted(richnessTable);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accs", accs)
                .addValue("positions", positions);
        try {
            jdbcTemplate.query(sql, params, rs -> {
                map.put(key(rs.getString("accession"), rs.getInt("position")),
                        new Richness(rs.getDouble("fun"), rs.getDouble("pop"), rs.getDouble("str")));
            });
        } catch (Exception e) {
            LOGGER.warn("Richness not loaded ({}): {}", richnessTable, e.getMessage());
        }
        return map;
    }
}
