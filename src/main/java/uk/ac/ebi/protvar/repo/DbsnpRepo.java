package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Dbsnp;

import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class DbsnpRepo {
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${tbl.dbsnp}")
    private String dbsnpTable;

    public List<Dbsnp> getById(List<Object[]> ids) {
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