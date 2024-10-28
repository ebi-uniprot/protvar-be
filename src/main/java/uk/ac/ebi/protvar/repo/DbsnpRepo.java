package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Dbsnp;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@AllArgsConstructor
public class DbsnpRepo {
    public static final String SELECT_DBSNP_WHERE_ID_IN = """
        SELECT DISTINCT d.* FROM dbsnp d
        INNER JOIN (VALUES :ids) AS t(id)
        ON t.id=d.id
        """;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<Dbsnp> getById(Set<Object[]> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return namedParameterJdbcTemplate.query(SELECT_DBSNP_WHERE_ID_IN, parameters, (rs, rowNum) -> createDbsnp(rs));
    }

    private Dbsnp createDbsnp(ResultSet rs) throws SQLException {
        Dbsnp dbsnp = new Dbsnp();
        dbsnp.setChr(rs.getString("chr"));
        dbsnp.setPos(rs.getInt("pos"));
        dbsnp.setId(rs.getString("id"));
        dbsnp.setRef(rs.getString("ref"));
        dbsnp.setAlt(rs.getString("alt"));
        return dbsnp;
    }
}