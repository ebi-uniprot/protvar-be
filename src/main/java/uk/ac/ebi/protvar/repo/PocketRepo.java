package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Pocket;
import uk.ac.ebi.protvar.utils.VariantKey;

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
    private static final String POCKET_FIELDS = """
        p.struct_id,
        p.pocket_id,
        p.pocket_rad_gyration AS rad_gyration,
        p.pocket_energy_per_vol AS energy_per_vol,
        p.pocket_buriedness AS buriedness,
        p.pocket_resid AS resid,
        p."pocket_pLDDT_mean" AS mean_plddt,
        p.pocket_score_combined_scaled AS score
        """;

    // Single accession-resid pair
    public Map<String, List<Pocket>> getPockets(String accession, Integer resid) {
        String sql = String.format("SELECT " + POCKET_FIELDS + """
            FROM %s p
            WHERE p.struct_id = :accession AND :resid = ANY(p.pocket_resid)
            ORDER BY p.pocket_score_combined_scaled DESC
            """, pocketTable); // sorting in DB for single accession-resid pair is faster than sorting in Java

        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("accession", accession)
                .addValue("resid", resid);

        List<Pocket> results = jdbcTemplate.query(sql, params, (rs, rowNum) -> createPocket(rs));
        if (results != null && !results.isEmpty())
            return Map.of(VariantKey.protein(accession, resid), results);
        return Map.of();
    }

    // Array of accession-residue pairs
    //Example input
    //P22304,205
    //P07949,783
    //P22309,71
    //Q9NUW8,493
    public Map<String, List<Pocket>> getPockets(String[] accessions, Integer[] positions) {
        if (accessions == null || accessions.length == 0)
            return Collections.emptyMap();

        String sql = String.format("SELECT " + POCKET_FIELDS + """
           , t.resid AS query_resid
        FROM %s p
        JOIN (
            SELECT unnest(:accessions) AS accession, unnest(:residues) AS resid
        ) t ON p.struct_id = t.accession
        WHERE t.resid = ANY(p.pocket_resid)
        ORDER BY p.struct_id, t.resid, p.pocket_score_combined_scaled DESC
        """, pocketTable);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accessions", accessions)
                .addValue("residues", positions);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, List<Pocket>> result = new HashMap<>();
            while (rs.next()) {
                Pocket pocket = createPocket(rs);
                result.computeIfAbsent(VariantKey.protein(rs.getString("struct_id"), rs.getInt("query_resid")),
                        k -> new ArrayList<>()).add(pocket);
            }
            //sortPocketsByScore(result);
            return result;
        });
    }

    /*
    If we ever need only the top N pockets per accession-resid pair, use:
    SELECT ...
         , ROW_NUMBER() OVER (PARTITION BY p.struct_id, t.resid ORDER BY p.pocket_score_combined_scaled DESC) AS rn
    FROM ...
    WHERE ...
    -- Optional filter:
    -- WHERE rn <= 3
     */


    /**
     * All pockets for a given accession
     * This will give a complete mapping of every residue that appears in at least one pocket, and which pockets they map to.
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
        String sql = String.format("SELECT " + POCKET_FIELDS + """
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
                result.computeIfAbsent(VariantKey.protein(structId, resid), k -> new ArrayList<>()).add(pocket);
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
