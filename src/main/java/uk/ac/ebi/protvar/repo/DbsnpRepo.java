package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Dbsnp;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@AllArgsConstructor
public class DbsnpRepo {
    @Value("${tbl.dbsnp}")
    private String dbsnpTable;
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<Dbsnp> getById(Set<Object[]> ids) {
        if (ids == null || ids.isEmpty())
            return new ArrayList<>();
        String sql = String.format("""
                SELECT DISTINCT d.* FROM %s d
                INNER JOIN (VALUES :ids) AS t(id)
                ON t.id=d.id
                """, dbsnpTable);
        SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
        return namedParameterJdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(Dbsnp.class));
    }
}