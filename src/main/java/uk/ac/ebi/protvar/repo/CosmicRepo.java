package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Cosmic;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@AllArgsConstructor
public class CosmicRepo {
    @Value("${tbl.cosmic}")
    private String cosmicTable;
    private NamedParameterJdbcTemplate jdbcTemplate;

    public List<Cosmic> getById(Set<Object[]> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        String sql = String.format("""
                SELECT DISTINCT c.id, c.chr, c.pos, c.ref, c.alt FROM %s c
                INNER JOIN (VALUES :ids) AS t(id)
                ON t.id=c.id
                """, cosmicTable);
        SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            Cosmic cosmic = createCosmic(rs);
            cosmic.setId(rs.getString("id"));
            return cosmic;
        });
    }

    public List<Cosmic> getByLegacyId(Set<Object[]> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        String sql = String.format("""
                SELECT DISTINCT c.legacy_id, c.chr, c.pos, c.ref, c.alt FROM %s c
                INNER JOIN (VALUES :ids) AS t(id)
                ON t.id=c.legacy_id
                """, cosmicTable);
        SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> {
            Cosmic cosmic = createCosmic(rs);
            cosmic.setLegacyId(rs.getString("legacy_id"));
            return cosmic;
        });
    }

    private Cosmic createCosmic(ResultSet rs) throws SQLException {
        return Cosmic.builder()
                .chr(rs.getString("chr"))
                .pos(rs.getInt("pos"))
                .ref(rs.getString("ref"))
                .alt(rs.getString("alt"))
                .build();
    }
}