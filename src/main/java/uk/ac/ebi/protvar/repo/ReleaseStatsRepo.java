package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.response.ReleaseStat;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReleaseStatsRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.release.stats}")
    private String statsTable;

    @Value("${tbl.release.stats.key}")
    private String keyTable;

    // Latest recorded value per key for the release (release_stats is append-only)
    public List<ReleaseStat> getReleaseStats(String release) {
        String sql = """
            SELECT k.key, k.label, k.description, s.value, s.source, s.version, s.source_table, s.created
            FROM %s k
            JOIN LATERAL (SELECT value, source, version, source_table, created FROM %s r
                          WHERE r.release = :release AND r.key = k.key
                          ORDER BY r.created DESC LIMIT 1) s ON true
            ORDER BY k.key
            """.formatted(keyTable, statsTable);
        return jdbcTemplate.query(sql, new MapSqlParameterSource("release", release), (rs, i) -> new ReleaseStat(
                rs.getString("key"), rs.getString("label"), rs.getString("description"), rs.getLong("value"), rs.getString("source"), rs.getString("version"), rs.getString("source_table"),
                rs.getTimestamp("created").toInstant(), null));
    }
}
