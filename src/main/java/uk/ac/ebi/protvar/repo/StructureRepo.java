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

    /**
     * SQL boolean fragment: true if {@code positionExpr} falls within any [start, end] range of
     * {@code observedRegionsRef} — a structure's observed_regions, the residues actually resolved.
     * observed_regions is a 2-D int array {{s1,e1},{s2,e2},...}, iterated via the built-in
     * {@code generate_subscripts}. Used by the PDB identifier search (MappingRepo / GenomicVariantRepo)
     * so it stays consistent with the 3D structure tab (StructureService.filterByPosition) — matching
     * the resolved residues rather than the full unp_start..unp_end span.
     *
     * @param observedRegionsRef SQL reference to the observed_regions column (e.g. "s.observed_regions")
     * @param positionExpr       SQL expression / bind param for the protein position (e.g. "m.protein_position" or ":position")
     */
    public static String observedRegionsContain(String observedRegionsRef, String positionExpr) {
        return String.format("""
            EXISTS (
                SELECT 1 FROM generate_subscripts(%1$s, 1) i
                WHERE %2$s BETWEEN %1$s[i][1] AND %1$s[i][2]
            )""", observedRegionsRef, positionExpr);
    }


    @Cacheable(value = "STR", key = "#accession")
    public List<Structure> getStr(String accession) {
        return getStr(List.of(accession));
    }

    // fetches structures for a list of accessions with a single query (not @Cacheable)
    public List<Structure> getStr(List<String> accessions) {
        if (accessions == null || accessions.isEmpty()) return Collections.emptyList();
        String sql = String.format("""
            SELECT * FROM %s WHERE accession IN (:accessions)
            ORDER BY accession, coverage DESC, resolution DESC
        """, structureTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accessions", accessions);
        return jdbcTemplate.query(sql, params, (rs, rowNum) -> createStructure(rs));
    }

    // NOTE: currently unused — StructureService.getStr(accession, position) fetches all
    // structures for the accession via getStr(accession) (cached) and filters by position in
    // Java (filterByPosition). Kept for a possible DB-side position filter; fixed to use
    // observedRegionsContain (the previous unnest(...)[1] form errored on the 2-D array and
    // never bound :position).
    public List<Structure> getStr(String accession, Integer position) {
        String sql = String.format("""
            SELECT * FROM %s
            WHERE accession = :accession
            AND %s
            ORDER BY coverage DESC, resolution DESC
        """, structureTable, observedRegionsContain("observed_regions", ":position"));
        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession)
                .addValue("position", position);
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
                rs.getInt("pdb_start"),
                rs.getInt("pdb_end"),
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
