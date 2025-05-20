package uk.ac.ebi.protvar.repo;

import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.response.Structure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class StructureRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.ann.str}")
    private String structureTable;


    @Cacheable(value = "STR", key = "#accession")
    public List<Structure> getStr(String accession) {
        String sql = String.format("""
 			SELECT * FROM %s WHERE accession=:accession
 			ORDER BY coverage DESC, resolution DESC
 			""", structureTable);

        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> createStructure(rs));
    }

    // fetches structures for a list of accessions with a single query (not @Cacheable)
    public List<Structure> getStr(List<String> accessions) {
        String sql = String.format("""
            SELECT * FROM %s WHERE accession IN (:accessions)
            ORDER BY accession, coverage DESC, resolution DESC
        """, structureTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accessions", accessions);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> createStructure(rs));
    }

    /*
    SELECT
        array_length(observed_regions, 1) AS num_regions,
        COUNT(*) AS num_rows
    FROM rel_2025_01_structure
    GROUP BY num_regions
    ORDER BY num_regions; --<null> 397 (SELECT * FROM rel_2025_01_structure WHERE observed_regions = '{}';)
     */
    public List<Structure> getStr(String accession, Integer position) {
        String sql = String.format("""
 			SELECT * FROM %s 
 			WHERE accession=:accession
 			AND EXISTS (
 				SELECT 1
 				FROM unnest(observed_regions) AS range(start_end)
 				WHERE :position BETWEEN start_end[1] AND start_end[2]
 			)
 			ORDER BY coverage DESC, resolution DESC
 			""", structureTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> createStructure(rs));
    }

    // Converts ResultSet row to Structure
    private Structure createStructure(ResultSet rs) throws SQLException {
        return new Structure(
                rs.getString("accession"),
                rs.getString("experimental_method"),
                rs.getDouble("resolution"),
                rs.getString("pdb_id"),
                rs.getString("chain_id"),
                get2DIntArray(rs, "observed_regions"),
                rs.getInt("start"),
                rs.getInt("end"),
                rs.getInt("unp_start"),
                rs.getInt("unp_end")
        );
    }

    private List<List<Integer>> get2DIntArray(ResultSet rs, String column) throws SQLException {
        Object[] outer = (Object[]) rs.getArray(column).getArray();
        List<List<Integer>> result = new ArrayList<>(outer.length);

        for (Object inner : outer) {
            Object[] innerArray = (Object[]) inner;
            List<Integer> row = new ArrayList<>(innerArray.length);
            for (Object val : innerArray) {
                row.add(((Number) val).intValue());
            }
            result.add(row);
        }

        return result;
    }
}
