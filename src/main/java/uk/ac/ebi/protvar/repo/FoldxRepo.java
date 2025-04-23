package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class FoldxRepo {

    @Value("${tbl.foldx}")
    private String foldxTable;

    private NamedParameterJdbcTemplate jdbcTemplate;


    // Single accession + single position + optional variant
    public List<Foldx> getFoldxs(String accession, Integer position, String variantAA) {
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
                .collect(Collectors.groupingBy(Foldx::getGroupBy));

        groupedFoldxs.values().forEach(this::groupAndReturnMiddleFragment);

        return groupedFoldxs.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
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
                .collect(Collectors.groupingBy(Foldx::getGroupBy));

        foldxsMap.values().forEach(this::groupAndReturnMiddleFragment);
        return foldxsMap;
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
