package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Humsavar;

import java.util.Collection;
import java.util.List;

/**
 * UniProt humsavar: one row per (VAR_ ID, disease); the protein change is the same on every row of an ID.
 */
@Repository
@RequiredArgsConstructor
public class HumsavarRepo {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.humsavar}")
    private String humsavarTable;

    public List<Humsavar> getByVarIds(Collection<String> varIds) {
        if (varIds == null || varIds.isEmpty())
            return List.of();
        String sql = String.format("""
                SELECT DISTINCT var_id, accession, position, wt_aa, mt_aa FROM %s
                WHERE var_id = ANY(:ids)
                """, humsavarTable);
        return jdbcTemplate.query(sql, new MapSqlParameterSource("ids", varIds.toArray(String[]::new)),
                (rs, rowNum) -> new Humsavar(rs.getString("var_id"), rs.getString("accession"),
                        rs.getInt("position"), rs.getString("wt_aa"), rs.getString("mt_aa")));
    }
}
