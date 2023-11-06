package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class ProtVarDataRepoImpl implements ProtVarDataRepo {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProtVarDataRepoImpl.class);

	private static final List EMPTY_RESULT = new ArrayList<>();

	private static final String SELECT_FROM_CADD_WHERE_CHR_POS_IN = "select * from cadd_prediction "
			+ "where (chromosome, position) in (:chrPosList)";

	private static final String SELECT_FROM_MAPPING_WHERE_CHR_POS_IN = "select * from genomic_protein_mapping " +
			"where (chromosome, genomic_position) in (:chrPosList) order by is_canonical desc";

	private static final String SELECT_FROM_MAPPING_WHERE_ACC_POS_IN = "select " +
			"chromosome, genomic_position, allele, accession, protein_position, protein_seq, codon, codon_position, reverse_strand  " +
			"from genomic_protein_mapping where " +
			"(accession, protein_position) in (:accPosList) ";

	// TODO CHECK - SQL Query Optimization for Large IN Queries
	private static final String SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL3 = "select " +
			"chromosome, genomic_position, allele, accession, protein_position, protein_seq, codon, codon_position, reverse_strand  " +
			"from genomic_protein_mapping " +
			"inner join ( " +
			"values ? ) as t(acc,pos) " +
			"on t.acc=accession and t.pos=protein_position ";

	private static final String SELECT_EVE_SCORES = "SELECT * FROM EVE_SCORE " +
			"WHERE (accession, position) IN (:protAccPositions) ";

	//private static final String SELECT_DBSNPS = "SELECT * FROM dbsnp WHERE id IN (:ids) ";
	private static final String SELECT_CROSSMAPS = "SELECT * FROM crossmap WHERE grch{VER}_pos IN (:pos) ";

	private static final String SELECT_CROSSMAPS2 = "SELECT * FROM crossmap " +
			"WHERE (chr, grch37_pos) IN (:chrPos37) ";

	// SQL syntax for array
	// search for only one value
	// SELECT * FROM af2_v3_human_pocketome WHERE struct_id='A0A075B6I1' AND 25=ANY(resid);
	// search array contains multiple value together (i.e. 24 AND 25)
	// SELECT * FROM af2_v3_human_pocketome WHERE struct_id='A0A075B6I1' AND resid @> '{24, 25}';
	// search array contains one of some values (i.e. 24 or 25)
	// SELECT * FROM af2_v3_human_pocketome WHERE struct_id='A0A075B6I1' AND resid && '{24, 25}';

	private static final String SELECT_POCKETS_BY_ACC_AND_RESID = "SELECT * FROM af2_v3_human_pocketome WHERE struct_id=:accession AND (:resid)=ANY(resid)";

	private static final String SELECT_FOLDXS_BY_ACC_AND_POS = "SELECT * FROM afdb_foldx WHERE protein_acc=:accession AND position=:position";
	private static final String SELECT_FOLDXS_BY_ACC_AND_POS_VARIANT = "SELECT * FROM afdb_foldx WHERE protein_acc=:accession AND position=:position AND mutated_type=:variantAA";

	private static final String SELECT_INTERACTIONS_BY_ACC_AND_RESID = "SELECT a, a_residues, b, b_residues, pdockq FROM af2complexes_interaction " +
																		"WHERE (a=:accession AND (:resid)=ANY(a_residues)) " +
																		"OR (b=:accession AND (:resid)=ANY(b_residues))";

	private static final String SELECT_INTERACTION_MODEL = "SELECT pdb_model FROM af2complexes_interaction WHERE a=:a AND b=:b";


	private static final String SELECT_CONSERV_SCORES = "SELECT * FROM CONSERV_SCORE WHERE acc=:acc " +
			"AND pos=:pos";

	private NamedParameterJdbcTemplate jdbcTemplate;

	@Override
	public List<CADDPrediction> getCADDByChrPos(List<Object[]> chrPosList) {
		if (chrPosList == null || chrPosList.isEmpty())
			return EMPTY_RESULT;
		SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);
		return jdbcTemplate.query(SELECT_FROM_CADD_WHERE_CHR_POS_IN, parameters, (rs, rowNum) -> createPrediction(rs));
	}

	private CADDPrediction createPrediction(ResultSet rs) throws SQLException {
		return new CADDPrediction(rs.getString("chromosome"), rs.getInt("position"), rs.getString("allele"),
				rs.getString("altallele"), rs.getDouble("rawscores"), rs.getDouble("scores"));
	}

	@Override
	public List<GenomeToProteinMapping> getMappingsByChrPos(List<Object[]> chrPosList) {
		if (chrPosList == null || chrPosList.isEmpty())
			return EMPTY_RESULT;
		SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);

		return jdbcTemplate.query(SELECT_FROM_MAPPING_WHERE_CHR_POS_IN, parameters, (rs, rowNum) -> createMapping(rs))
				.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	private GenomeToProteinMapping createMapping(ResultSet rs) throws SQLException {
		return GenomeToProteinMapping.builder()
				.chromosome(rs.getString("chromosome"))
				.genomeLocation(rs.getInt("genomic_position"))
				.isoformPosition(rs.getInt("protein_position"))
				.baseNucleotide(rs.getString("allele"))
				.aa(rs.getString("protein_seq"))
				.codon(rs.getString("codon"))
				.accession(rs.getString("accession"))
				.ensg(ensXVersion(rs.getString("ensg"), rs.getString("ensg_ver")))
				.ensp(ensXVersion(rs.getString("ensp"), rs.getString("ensp_ver")))
				.enst(ensXVersion(rs.getString("enst"), rs.getString("enst_ver")))
				.ense(rs.getString("ense"))
				.reverseStrand(rs.getBoolean("reverse_strand"))
				.isValidRecord(rs.getBoolean("is_match"))
				.patchName(rs.getString("patch_name"))
				.geneName(rs.getString("gene_name"))
				.codonPosition(rs.getInt("codon_position"))
				.isCanonical(rs.getBoolean("is_canonical"))
				.isManeSelect(rs.getBoolean("is_mane_select"))
				.proteinName(rs.getString("protein_name"))
				.build();
	}

	private String ensXVersion(String ens, String ver) {
		return (ens == null ? "" : ens) + "." + (ver == null ? "" : ver);
	}

	public List<GenomeToProteinMapping> getMappingsByAccPos(List<Object[]> accPosList) {
		if (accPosList == null || accPosList.isEmpty())
			return EMPTY_RESULT;
		SqlParameterSource parameters = new MapSqlParameterSource("accPPosition", accPosList);

		return jdbcTemplate.query(SELECT_FROM_MAPPING_WHERE_ACC_POS_IN, parameters, (rs, rowNum) ->
						GenomeToProteinMapping.builder()
								.chromosome(rs.getString("chromosome"))
								.genomeLocation(rs.getInt("genomic_position"))
								.baseNucleotide(rs.getString("allele"))
								.accession(rs.getString("accession"))
								.isoformPosition(rs.getInt("protein_position"))
								.aa(rs.getString("protein_seq"))
								.codon(rs.getString("codon"))
								.codonPosition(rs.getInt("codon_position"))
								.reverseStrand(rs.getBoolean("reverse_strand")).build())
				.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	public double getPercentageMatch(List<Object[]> chrPosRefList, String ver) {
		String sql = "SELECT 100 * COUNT (DISTINCT (chr, grchVER_pos, grchVER_base)) / :num " +
				"FROM crossmap " +
				"WHERE (chr, grchVER_pos, grchVER_base) " +
				"IN (:chrPosRef)";

		sql = sql.replaceAll("VER", ver);

		SqlParameterSource parameters = new MapSqlParameterSource("num", chrPosRefList.size())
				.addValue("chrPosRef", chrPosRefList);

		return jdbcTemplate.queryForObject(sql, parameters, Integer.class);
	}

	public List<EVEScore> getEVEScores(Set<String> protAccPositions) {
		Set<Object[]> protAccPositionsObjSet = new HashSet<>();
		for (String accPos : protAccPositions) {
			String[] arr = accPos.split(":");
			if (arr.length == 2) {
				String acc = arr[0];
				try {
					int pos = Integer.parseInt(arr[1]);
					protAccPositionsObjSet.add(new Object[]{acc, pos });
				} catch (NumberFormatException ex) {
					// do nothing
				}
			}
		}
		if (protAccPositionsObjSet.size() > 0) {
			SqlParameterSource parameters = new MapSqlParameterSource("protAccPositions", protAccPositionsObjSet);
			return jdbcTemplate.query(SELECT_EVE_SCORES, parameters, (rs, rowNum) -> createEveScore(rs));
		}
		return new ArrayList<>();
	}
/*
	@Override
	public List<Dbsnp> getDbsnps(List<String> ids) {
		if (ids.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
		return jdbcTemplate.query(SELECT_DBSNPS, parameters, (rs, rowNum) ->
				new Dbsnp(rs.getString("chr"), rs.getInt("pos"), rs.getString("id"),
						rs.getString("ref"),rs.getString("alt")));
	}
*/
	public List<Crossmap> getCrossmaps(List<Integer> positions, String from) {
		if (positions.isEmpty())
			return new ArrayList<>();
		String sql = SELECT_CROSSMAPS.replace("{VER}", from);
		SqlParameterSource parameters = new MapSqlParameterSource("pos", positions);
		return jdbcTemplate.query(sql, parameters, (rs, rowNum) ->
				new Crossmap(rs.getString("chr"), rs.getInt("grch38_pos"), rs.getString("grch38_base"),
						rs.getInt("grch37_pos"),rs.getString("grch37_base")));
	}

	public List<Crossmap> getCrossmapsByChrPos37(List<Object[]> chrPos37) {
		if (chrPos37.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("chrPos37", chrPos37);
		return jdbcTemplate.query(SELECT_CROSSMAPS2, parameters, (rs, rowNum) ->
				new Crossmap(rs.getString("chr"), rs.getInt("grch38_pos"), rs.getString("grch38_base"),
						rs.getInt("grch37_pos"),rs.getString("grch37_base")));
	}

	private EVEScore createEveScore(ResultSet rs) throws SQLException {
		return new EVEScore(rs.getString("accession"), rs.getInt("position"), rs.getString("wt_aa"),
				rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
	}

	//================================================================================
	// Pocket, foldx and interaction
	//================================================================================

	public List<Pocket> getPockets(String accession, Integer resid) {
		SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
				.addValue("resid", resid);
		return jdbcTemplate.query(SELECT_POCKETS_BY_ACC_AND_RESID, parameters, (rs, rowNum) -> createPocket(rs));
	}

	public List<Foldx> getFoldxs(String accession, Integer position, String variantAA) {
		SqlParameterSource parameters;
		String query;
		if (variantAA != null && !variantAA.isEmpty()) {
			parameters = new MapSqlParameterSource("accession", accession)
					.addValue("position", position)
					.addValue("variantAA", variantAA);
			query = SELECT_FOLDXS_BY_ACC_AND_POS_VARIANT;
		} else {
			parameters = new MapSqlParameterSource("accession", accession)
					.addValue("position", position);
			query = SELECT_FOLDXS_BY_ACC_AND_POS;
		}
		return jdbcTemplate.query(query, parameters, (rs, rowNum) -> createFoldx(rs));
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
		return new Pocket(rs.getString("struct_id"), rs.getDouble("energy"), rs.getDouble("energy_per_vol"),
				rs.getDouble("score"), getResidueList(rs, "resid"));
	}

	private Foldx createFoldx(ResultSet rs) throws SQLException  {
		return new Foldx(rs.getString("protein_acc"), rs.getInt("position"), rs.getString("wild_type"),
				rs.getString("mutated_type"), rs.getDouble("foldx_ddg"), rs.getDouble("plddt"));
	}

	private Interaction createInteraction(ResultSet rs) throws SQLException  {
		return new Interaction(rs.getString("a"), getResidueList(rs, "a_residues"),
				rs.getString("b"), getResidueList(rs, "b_residues"),
				rs.getDouble("pdockq"));
	}

	private List<Integer> getResidueList(ResultSet rs, String fieldName) throws SQLException  {
		Integer[] residArr = (Integer[])  rs.getArray(fieldName).getArray();
		return Arrays.asList(residArr);
	}

	public List<ConservScore> getConservScores(String acc, Integer pos) {
		SqlParameterSource parameters = new MapSqlParameterSource("acc", acc)
				.addValue("pos", pos);
		return jdbcTemplate.query(SELECT_CONSERV_SCORES, parameters, (rs, rowNum) -> createConservScore(rs));
	}

	private ConservScore createConservScore(ResultSet rs) throws SQLException {
		return new ConservScore(rs.getString("acc"), rs.getString("aa"),
				rs.getInt("pos"), rs.getDouble("score"));
	}
}
