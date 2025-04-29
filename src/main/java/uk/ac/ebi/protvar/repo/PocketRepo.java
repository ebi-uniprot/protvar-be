package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Pocket;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class PocketRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.pocket.v2}")
    private String pocketTable;

    // SQL syntax for array
    // search for only one value
    // SELECT * FROM pocket WHERE struct_id='A0A075B6I1' AND 25=ANY(resid);
    // search array contains multiple value together (i.e. 24 AND 25)
    // SELECT * FROM pocket WHERE struct_id='A0A075B6I1' AND resid @> '{24, 25}';
    // search array contains one of some values (i.e. 24 or 25)
    // SELECT * FROM pocket WHERE struct_id='A0A075B6I1' AND resid && '{24, 25}';

    private static final String SELECT_POCKET_BY_ACC_AND_RESID = """
 			SELECT * FROM pocket 
 			WHERE struct_id=:accession AND (:resid)=ANY(resid)
 			""";

    // Note: score in v1 is score_combined_scaled in v2
    private static final String SELECT_ = """
        SELECT p.struct_id 
             , p.pocket_id
             , p.pocket_rad_gyration AS rad_gyration
             , p.pocket_energy_per_vol AS energy_per_vol
             , p.pocket_buriedness AS buriedness
             , p.pocket_resid AS resid
             , p."pocket_pLDDT_mean" AS mean_plddt
             , p.pocket_score_combined_scaled AS score
        """;

    // Query 1: Single accession + single residue
    public List<Pocket> getPockets(String accession, Integer resid) {
        String sql = String.format(SELECT_ + """
            FROM %s p
            WHERE p.struct_id = :accession AND :resid = ANY(p.pocket_resid)
            ORDER BY p.pocket_score_combined_scaled DESC
            """, pocketTable); // sorting in DB for single accession-resid pair is faster than sorting in Java

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("resid", resid);

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> createPocket(rs));
    }

    // Query 2: Multiple accession-residue pairs
    public Map<String, List<Pocket>> getPockets(List<Object[]> accResidList) {
        if (accResidList == null || accResidList.isEmpty()) return Collections.emptyMap();

        // Build VALUES part dynamically
        StringBuilder valuesClause = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();

        for (int i = 0; i < accResidList.size(); i++) {
            String accParam = "acc" + i;
            String residParam = "resid" + i;

            if (i > 0) valuesClause.append(", ");
            valuesClause.append(String.format("(:%s, :%s)", accParam, residParam));

            params.addValue(accParam, accResidList.get(i)[0]); // accession
            params.addValue(residParam, accResidList.get(i)[1]); // resid
        }

        String sql = String.format(SELECT_ + """
               , t.resid AS query_resid 
            FROM %s p 
            """, pocketTable) + """
            JOIN ( VALUES 
            """ + valuesClause + """
            ) AS t(accession, resid)
              ON p.struct_id = t.accession
            WHERE t.resid = ANY(p.pocket_resid)
            """;

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<Pocket>> result = new HashMap<>();
            while (rs.next()) {
                Pocket pocket = createPocket(rs);
                String key = rs.getString("struct_id") + "-" + rs.getInt("query_resid");
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(pocket);
            }
            // Sort pockets by score for each key
            sortPocketsByScore(result);
            return result;
        });
    }

    // Query 3: Single accession + multiple residues
    public Map<String, List<Pocket>> getPockets(String accession, List<Integer> residues) {
        if (residues == null || residues.isEmpty()) return Collections.emptyMap();
        String sql =  String.format(SELECT_ + """
               , ARRAY(
                           SELECT unnest(p.pocket_resid)
                           INTERSECT
                           SELECT unnest(:residues)
                       ) AS matched_residues 
            FROM %s p 
            """, pocketTable) + """
                WHERE p.struct_id = :accession
                  AND p.pocket_resid && :residues
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("residues", residues);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<Pocket>> result = mapPocketsByResidue(rs, "struct_id", "matched_residues");
            sortPocketsByScore(result);
            return result;
        });
    }

    /**
     * Query 4: All residues for a given accession
     * This will give you a complete mapping of every residue that appears in at least one pocket, and which pockets they map to.
     *
     * {
     *   acc-12 -> [PocketA, PocketC],
     *   acc-45 -> [PocketA],
     *   acc-67 -> [PocketB]
     * }
     * @param accession
     * @return accession-residue mapping
     */
    @Cacheable(value = "pocketsByAccession", key = "#accession")
    public Map<String, List<Pocket>> getPockets(String accession) {
        String sql = String.format(SELECT_ + """
        FROM %s p
        WHERE p.struct_id = :accession
        """, pocketTable);

        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<Pocket>> result = mapPocketsByResidue(rs, "struct_id", "resid");
            sortPocketsByScore(result);
            return result;
        });
    }

    private Map<String, List<Pocket>> mapPocketsByResidue(ResultSet rs, String structIdColumn, String matchedResidueColumn) throws SQLException {
        Map<String, List<Pocket>> result = new HashMap<>();
        while (rs.next()) {
            Pocket pocket = createPocket(rs);
            Array residArray = rs.getArray(matchedResidueColumn);
            if (residArray == null) continue;

            Integer[] residues = (Integer[]) residArray.getArray();
            String structId = rs.getString(structIdColumn);

            for (Integer resid : residues) {
                String key = structId + "-" + resid;
                result.computeIfAbsent(key, k -> new ArrayList<>()).add(pocket);
            }
        }
        return result;
    }

    // Converts ResultSet row to Pocket
    private Pocket createPocket(ResultSet rs) throws SQLException {
        return new Pocket(
                rs.getString("struct_id"),
                rs.getInt("pocket_id"),
                rs.getDouble("rad_gyration"),
                rs.getDouble("energy_per_vol"),
                rs.getDouble("buriedness"),
                Arrays.asList((Integer[]) rs.getArray("resid").getArray()),
                rs.getDouble("mean_plddt"),
                rs.getDouble("score"));
    }

    // Sorts each list of pockets by score (descending)
    private <T> void sortPocketsByScore(Map<T, List<Pocket>> map) {
        map.values().forEach(list ->
                list.sort(Comparator.comparing(Pocket::getScore).reversed())
        );
    }
}
