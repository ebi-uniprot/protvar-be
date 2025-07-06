package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Foldx;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FoldxRepo {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.foldx}")
    private String foldxTable;


    // Single accession + single position + optional variant
    public Map<String, List<Foldx>> getFoldxs(String accession, Integer position, String variantAA) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("accession", accession);
        parameters.addValue("position", position);

        StringBuilder sql = new StringBuilder(String.format("""
				SELECT * FROM %s 
				WHERE protein_acc=:accession 
				AND position=:position 
				""", foldxTable));

        if (variantAA != null && !variantAA.isEmpty()) {
            parameters.addValue("variantAA", variantAA);
            sql.append(" AND mutated_type = :variantAA");
        }

        List<Foldx> foldxs = jdbcTemplate.query(sql.toString(), parameters, (rs, rowNum) -> createFoldx(rs));
        // Group by accession-position-variantAA and return only the middle fragment
        Map<String, List<Foldx>> groupedFoldxs = foldxs.stream()
                .collect(Collectors.groupingBy(Foldx::getVariantKey));

        groupedFoldxs.values().forEach(this::groupAndReturnMiddleFragment);

        return groupedFoldxs;
    }

    // All positions for a given accession, grouped by position and variantAA

    /**
     *
     * @param accession
     * @return accession-position-variantAA mapping
     */
    public Map<String, List<Foldx>> getFoldxs(String accession) {
        String sql = String.format("""
				SELECT * FROM %s 
				WHERE protein_acc=:accession 
				""", foldxTable);
        MapSqlParameterSource parameters = new MapSqlParameterSource("accession", accession);
        List<Foldx> foldxs = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> createFoldx(rs));

        // Group by accession-position-variantAA and return only the middle fragment
        Map<String, List<Foldx>> foldxsMap = foldxs.stream()
                .collect(Collectors.groupingBy(Foldx::getVariantKey));

        foldxsMap.values().forEach(this::groupAndReturnMiddleFragment);
        return foldxsMap;
    }

    public Map<String, List<Foldx>> getFoldxs(String[] accessions, Integer[] positions) {
        if (accessions == null || accessions.length == 0) {
            return Collections.emptyMap();
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accessions", accessions)
                .addValue("positions", positions);

        String sql = String.format("""
        SELECT f.* FROM %s f
        JOIN (
            SELECT unnest(:accessions) AS acc, unnest(:positions) AS pos
        ) AS t ON f.protein_acc = t.acc AND f.position = t.pos
        """, foldxTable);

        List<Foldx> foldxs = jdbcTemplate.query(sql, params, (rs, rowNum) -> createFoldx(rs));

        Map<String, List<Foldx>> grouped = foldxs.stream()
                .collect(Collectors.groupingBy(Foldx::getVariantKey));

        grouped.values().forEach(this::groupAndReturnMiddleFragment);

        return grouped;
    }

    // Helper method to group by accession-position-variantAA and return the middle fragment
    private List<Foldx> groupAndReturnMiddleFragment(List<Foldx> foldxs) {
        if (foldxs.size() == 1) {
            return foldxs; // Single fragment, return as is
        }

        foldxs.sort(Comparator.comparing(Foldx::getAfId));  // Sort by afId

        // Get middle element
        int middleIndex = foldxs.size() / 2;
        Foldx middleElement = foldxs.get(middleIndex);
        middleElement.setNumFragments(foldxs.size());  // Set the number of fragments

        return List.of(middleElement);  // Return list containing only the middle fragment
    }

    // Converts ResultSet row to Foldx
    private Foldx createFoldx(ResultSet rs) throws SQLException {
        return new Foldx(
                rs.getString("protein_acc"),
                rs.getInt("position"),
                rs.getString("af_id"),
                rs.getInt("af_pos"),
                rs.getString("wild_type"),
                rs.getString("mutated_type"),
                rs.getDouble("foldx_ddg"),
                rs.getDouble("plddt"), 1);
    }

}
