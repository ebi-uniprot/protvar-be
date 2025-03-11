package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CADD, Foldx, pocket, and protein interaction
 */
@Repository
@AllArgsConstructor
public class PredictionRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(PredictionRepo.class);

    @Value("${tbl.cadd}")
    private String caddTable;
    @Value("${tbl.foldx}")
    private String foldxTable;

    private NamedParameterJdbcTemplate jdbcTemplate;

    private static final String CADDS_IN_CHR_POS = """
   			SELECT * FROM %s 
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos) 
   			ON t.chr=chromosome AND t.pos=position
   			"""; // optimised from: SELECT * FROM <tbl.cadd> WHERE (chromosome,position) IN (:chrPosList)
    // to avoid max num of "in" values reached. Refer to https://stackoverflow.com/questions/1009706/

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

    // v2 score is scaled combined score
    private static final String SELECT_POCKET_V2_BY_ACC_AND_RESID = """
			SELECT struct_id, pocket_id,
				pocket_rad_gyration as rad_gyration,
				pocket_energy_per_vol as energy_per_vol,
				pocket_buriedness as buriedness,
				pocket_resid as resid,
				"pocket_pLDDT_mean" as mean_plddt,
				pocket_score_combined_scaled as score
			FROM pocket_v2
			WHERE struct_id=:accession AND (:resid)=ANY(pocket_resid)
			ORDER BY pocket_score_combined_scaled DESC
 			"""; //with score in v1 is score_combined_scaled in v2

    private static final String SELECT_INTERACTIONS_BY_ACC_AND_RESID = """
			SELECT a, a_residues, b, b_residues, pdockq 
			FROM af2complexes_interaction 
			WHERE (a=:accession AND (:resid)=ANY(a_residues)) 
			OR (b=:accession AND (:resid)=ANY(b_residues))
			""";

    private static final String SELECT_INTERACTIONS_BY_ACC_AND_RESID_NEW = """
		   SELECT a, ("a_residues_5A" || "a_residues_8A") as a_residues, 
		   b, ("b_residues_5A" || "b_residues_8A") as b_residues, pdockq 
		   FROM interaction_v2 
		   WHERE (a=:accession AND (:resid)=ANY("a_residues_5A" || "a_residues_8A")) 
		   OR (b=:accession AND (:resid)=ANY("b_residues_5A" || "b_residues_8A"))
		   """;
    private static final String SELECT_INTERACTION_MODEL = "SELECT pdb_model FROM af2complexes_interaction WHERE a=:a AND b=:b";
    private static final String SELECT_INTERACTION_MODEL_NEW = "SELECT pdb_model FROM interaction_v2 WHERE a=:a AND b=:b";

    private static final String FOLDX = """
    		select 'FOLDX' as type, mutated_type as mt_aa, foldx_ddg as score, null as class
    		from conserv_score 
    		where protein_acc=:acc and position=:pos
 			"""; // mutated_type=:mt

    public List<CADDPrediction> getCADDByChrPos(List<Object[]> chrPosList) {
        if (chrPosList == null || chrPosList.isEmpty())
            return List.of();
        SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);
        String sql = String.format(CADDS_IN_CHR_POS, caddTable);
        return jdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(CADDPrediction.class));
    }

    public List<Foldx> getFoldxs(String accession, Integer position, String variantAA) {
        MapSqlParameterSource  parameters = new MapSqlParameterSource();
        parameters.addValue("accession", accession);
        parameters.addValue("position", position);
        String sql = String.format("""
				SELECT * FROM %s 
				WHERE protein_acc=:accession 
				AND position=:position 
				""", foldxTable);

        if (variantAA != null && !variantAA.isEmpty()) {
            parameters.addValue("variantAA", variantAA);
            sql += " AND mutated_type=:variantAA";
        }

        List<Foldx> foldxs = jdbcTemplate.query(sql, parameters, (rs, rowNum) -> createFoldx(rs));
        Map<String, List<Foldx>> foldxsMap = foldxs.stream().collect(Collectors.groupingBy(Foldx::getGroupBy));

        if (foldxs.size() == foldxsMap.size()) // no protein with multiple fragments
            return foldxs;

        List<Foldx> newFoldxs = new ArrayList<>();
        for (List<Foldx> foldxList : foldxsMap.values()) {
            // Sort by afId
            foldxList.sort(Comparator.comparing(Foldx::getAfId));
            // Get middle element
            int numFragments = foldxList.size();
            int middleIndex = numFragments / 2;
            Foldx middleElement = foldxList.get(middleIndex);
            middleElement.setNumFragments(numFragments);
            newFoldxs.add(middleElement);
        }

        return newFoldxs;
    }

    public List<Pocket> getPockets(String accession, Integer resid) {
        SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
                .addValue("resid", resid);
        return jdbcTemplate.query(SELECT_POCKET_V2_BY_ACC_AND_RESID, parameters, (rs, rowNum) -> createPocket(rs));
    }

    public List<Interaction> getInteractions(String accession, Integer resid) {
        SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
                .addValue("resid", resid);
        return jdbcTemplate.query(SELECT_INTERACTIONS_BY_ACC_AND_RESID, parameters, (rs, rowNum) -> createInteraction(rs));
    }

    public String getInteractionModel(String a, String b) {
        SqlParameterSource parameters = new MapSqlParameterSource("a", a)
                .addValue("b", b);
        try {
            return jdbcTemplate.queryForObject(SELECT_INTERACTION_MODEL, parameters, (rs, rowNum) ->
                    rs.getString("pdb_model"));
        }
        catch (EmptyResultDataAccessException e) {
            LOGGER.warn("getInteractionModel returned empty result");
        }
        return null;
    }

    private Pocket createPocket(ResultSet rs) throws SQLException  {
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

    private Foldx createFoldx(ResultSet rs) throws SQLException  {
        return new Foldx(rs.getString("protein_acc"), rs.getInt("position"),
                rs.getString("af_id"), rs.getInt("af_pos"), rs.getString("wild_type"),
                rs.getString("mutated_type"), rs.getDouble("foldx_ddg"), rs.getDouble("plddt"), 1);
    }

    private Interaction createInteraction(ResultSet rs) throws SQLException  {
        return new Interaction(
                rs.getString("a"),
                Arrays.asList((Integer[]) rs.getArray("a_residues").getArray()),
                rs.getString("b"),
                Arrays.asList((Integer[]) rs.getArray("b_residues").getArray()),
                rs.getDouble("pdockq"));
    }
}
