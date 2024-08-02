package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
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
    public static final String SELECT_COSMIC_WHERE_ID_IN = """
            SELECT DISTINCT c.* FROM cosmic c
            INNER JOIN (VALUES :ids) AS t(id)
            ON t.id=c.id
            """;
    public static final String SELECT_COSMIC_WHERE_LEGACY_ID_IN = """
            SELECT DISTINCT c.* FROM cosmic c
            INNER JOIN (VALUES :ids) AS t(id)
            ON t.id=c.legacy_id
            """;

    private NamedParameterJdbcTemplate jdbcTemplate;

    public List<Cosmic> getById(Set<Object[]> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return jdbcTemplate.query(SELECT_COSMIC_WHERE_ID_IN, parameters, (rs, rowNum) -> createCosmic(rs));
    }

    public List<Cosmic> getByLegacyId(Set<Object[]> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return jdbcTemplate.query(SELECT_COSMIC_WHERE_LEGACY_ID_IN, parameters, (rs, rowNum) -> createCosmic(rs));
    }

    private Cosmic createCosmic(ResultSet rs) throws SQLException {
        Cosmic cosmic = new Cosmic();
        cosmic.setId(rs.getString("id"));
        cosmic.setLegacyId(rs.getString("legacy_id"));
        cosmic.setChr(rs.getString("chr"));
        cosmic.setPos(rs.getInt("pos"));
        cosmic.setRef(rs.getString("ref"));
        cosmic.setAlt(rs.getString("alt"));
        return cosmic;
    }
}