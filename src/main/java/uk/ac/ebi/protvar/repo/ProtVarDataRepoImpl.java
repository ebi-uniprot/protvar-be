package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.grc.Crossmap;
import uk.ac.ebi.protvar.model.response.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class ProtVarDataRepoImpl implements ProtVarDataRepo {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProtVarDataRepoImpl.class);

	private static final String SELECT_PREDICTIONS_BY_POSITIONS = "select chromosome, position, allele, altallele, rawscores, scores "
			+ "from CADD_PREDICTION where position in (:position)";

	private static final String[] chromosomes = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13",
			"14", "15", "16", "17", "18", "19", "20", "21", "22", "X", "Y" };

	private static final String SELECT_MAPPINGS_SQL = "select " +
			"chromosome, protein_position, protein_seq, genomic_position, allele, codon, accession, reverse_strand, " +
			"ensg, ensg_ver, ensp, ensp_ver, enst, enst_ver, " +
			"ense, patch_name, is_match, gene_name, codon_position, is_canonical, is_mane_select, protein_name " +
			"from genomic_protein_mapping where chromosome = :chromosome and genomic_position = :position  order by is_canonical desc";
	
	private static final String SELECT_MAPPINGS_BY_POSITION_SQL = "select " +
			"chromosome, protein_position, protein_seq, genomic_position, allele, codon, accession, reverse_strand, " +
			"ensg, ensg_ver, ensp, ensp_ver, enst, enst_ver, " +
			"ense, patch_name, is_match, gene_name, codon_position, is_canonical, is_mane_select, protein_name " +
			"from genomic_protein_mapping where genomic_position in (:position)  order by is_canonical desc";

	private static final String SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL = "select " +
			"chromosome, allele, genomic_position, protein_position, codon, reverse_strand, codon_position " +
			"from genomic_protein_mapping where protein_position = :proteinPosition " +
			"and accession = :accession " +
			"and codon_position in (:codonPositions) order by is_canonical desc";

	private static final String SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL2 = "select " +
			"accession, protein_position, chromosome, genomic_position, allele, codon, reverse_strand, codon_position " +
			"from genomic_protein_mapping where " +
			"(accession, protein_position) in (:accPPosition) ";

	private static final String SELECT_EVE_SCORES = "SELECT * FROM EVE_SCORE WHERE accession IN (:accessions) " +
			"AND position IN (:positions)";

	private static final String SELECT_EVE_SCORES2 = "SELECT * FROM EVE_SCORE " +
			"WHERE (accession, position) IN (:protAccPositions) ";

	private static final String SELECT_DBSNPS = "SELECT * FROM dbsnp WHERE id IN (:ids) ";
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

	private static final String SELECT_FOLDXS_BY_ACC_AND_POS = "SELECT * FROM af2_snps_foldx WHERE protein_acc=:accession AND position=:position";

	private static final String SELECT_INTERACTIONS_BY_ACC_AND_RESID = "SELECT a, a_residues, b, b_residues, pdockq FROM af2complexes_interaction " +
																		"WHERE (a=:accession AND (:resid)=ANY(a_residues)) " +
																		"OR (b=:accession AND (:resid)=ANY(b_residues))";

	private static final String SELECT_INTERACTION_MODEL = "SELECT pdb_model FROM af2complexes_interaction WHERE a=:a AND b=:b";


	private static final String SELECT_CONSERV_SCORES = "SELECT * FROM CONSERV_SCORE WHERE acc=:acc " +
			"AND pos=:pos";

	private NamedParameterJdbcTemplate jdbcTemplate;
	
	@Override
	public List<CADDPrediction> getCADDPredictions(Set<Long> positions) {
		SqlParameterSource parameters = new MapSqlParameterSource("position", positions);
		return jdbcTemplate.query(SELECT_PREDICTIONS_BY_POSITIONS, parameters, (rs, rowNum) -> createPrediction(rs));
	}

	private CADDPrediction createPrediction(ResultSet rs) throws SQLException {
		return new CADDPrediction(rs.getString("chromosome"), rs.getLong("position"), rs.getString("allele"),
				rs.getString("altallele"), rs.getDouble("rawscores"), rs.getDouble("scores"));
	}

	@Override
	public List<GenomeToProteinMapping> getMappings(String chromosome, Long position) {
		SqlParameterSource parameters = new MapSqlParameterSource("position", position).addValue("chromosome",
				chromosome);

		return jdbcTemplate.query(SELECT_MAPPINGS_SQL, parameters, (rs, rowNum) -> createMapping(rs))
			.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	private String ensXVersion(String ens, String ver) {
		return (ens == null ? "" : ens) + "." + (ver == null ? "" : ver);
	}

	private GenomeToProteinMapping createMapping(ResultSet rs) throws SQLException {
		return GenomeToProteinMapping.builder()
				.chromosome(rs.getString("chromosome"))
				.genomeLocation(rs.getLong("genomic_position"))
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

	@Override
	public List<GenomeToProteinMapping> getMappings(Set<Long> positions) {
		SqlParameterSource parameters = new MapSqlParameterSource("position", positions);

		return jdbcTemplate.query(SELECT_MAPPINGS_BY_POSITION_SQL, parameters, (rs, rowNum) -> createMapping(rs))
			.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}
	public List<GenomeToProteinMapping> getMappings(String accession, Long proteinPosition, Set<Integer> codonPositions) {
		SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
				.addValue("proteinPosition", proteinPosition)
				.addValue("codonPositions", codonPositions);
		return jdbcTemplate.query(SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL, parameters, (rs, rowNum) ->
						GenomeToProteinMapping.builder()
								.chromosome(rs.getString("chromosome"))
								.baseNucleotide(rs.getString("allele"))
								.genomeLocation(rs.getLong("genomic_position"))
								.isoformPosition(rs.getInt("protein_position"))
								.codon(rs.getString("codon"))
								.reverseStrand(rs.getBoolean("reverse_strand"))
								.codonPosition(rs.getInt("codon_position")).build())
				.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	public List<GenomeToProteinMapping> getGenomicCoordsByProteinAccAndPos(List<Object[]> accPPosition) {
		SqlParameterSource parameters = new MapSqlParameterSource("accPPosition", accPPosition);

		return jdbcTemplate.query(SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL2, parameters, (rs, rowNum) ->
						GenomeToProteinMapping.builder()
								.accession(rs.getString("accession"))
								.chromosome(rs.getString("chromosome"))
								.baseNucleotide(rs.getString("allele"))
								.genomeLocation(rs.getLong("genomic_position"))
								.isoformPosition(rs.getInt("protein_position"))
								.codon(rs.getString("codon"))
								.reverseStrand(rs.getBoolean("reverse_strand"))
								.codonPosition(rs.getInt("codon_position")).build())
				.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	public List<EVEScore> getEVEScores(List<String> accessions, List<Integer> positions) {
		if (accessions.isEmpty() || positions.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("accessions", accessions)
				.addValue("positions", positions);
		return jdbcTemplate.query(SELECT_EVE_SCORES, parameters, (rs, rowNum) -> createEveScore(rs));
	}

	public List<EVEScore> getEVEScores(List<Object[]> protAccPositions) {
		if (protAccPositions.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("protAccPositions", protAccPositions);
		return jdbcTemplate.query(SELECT_EVE_SCORES2, parameters, (rs, rowNum) -> createEveScore(rs));
	}

	@Override
	public List<Dbsnp> getDbsnps(List<String> ids) {
		if (ids.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
		return jdbcTemplate.query(SELECT_DBSNPS, parameters, (rs, rowNum) ->
				new Dbsnp(rs.getString("chr"), rs.getLong("pos"), rs.getString("id"),
						rs.getString("ref"),rs.getString("alt")));
	}

	public List<Crossmap> getCrossmaps(List<Long> positions, String from) {
		if (positions.isEmpty())
			return new ArrayList<>();
		String sql = SELECT_CROSSMAPS.replace("{VER}", from);
		SqlParameterSource parameters = new MapSqlParameterSource("pos", positions);
		return jdbcTemplate.query(sql, parameters, (rs, rowNum) ->
				new Crossmap(rs.getString("chr"), rs.getLong("grch38_pos"), rs.getString("grch38_base"),
						rs.getLong("grch37_pos"),rs.getString("grch37_base")));
	}

	public List<Crossmap> getCrossmapsByChrPos37(List<Object[]> chrPos37) {
		if (chrPos37.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("chrPos37", chrPos37);
		return jdbcTemplate.query(SELECT_CROSSMAPS2, parameters, (rs, rowNum) ->
				new Crossmap(rs.getString("chr"), rs.getLong("grch38_pos"), rs.getString("grch38_base"),
						rs.getLong("grch37_pos"),rs.getString("grch37_base")));
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

	public List<Foldx> getFoldxs(String accession, Integer position) {
		SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
				.addValue("position", position);
		return jdbcTemplate.query(SELECT_FOLDXS_BY_ACC_AND_POS, parameters, (rs, rowNum) -> createFoldx(rs));
	}

	public List<Interaction> getInteractions(String accession, Integer resid) {
		SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
				.addValue("resid", resid);
		return jdbcTemplate.query(SELECT_INTERACTIONS_BY_ACC_AND_RESID, parameters, (rs, rowNum) -> createInteraction(rs));
	}

	public String getInteractionModel(String a, String b) {
		SqlParameterSource parameters = new MapSqlParameterSource("a", a)
				.addValue("b", b);
		return jdbcTemplate.queryForObject(SELECT_INTERACTION_MODEL, parameters, (rs, rowNum) ->
				rs.getString("pdb_model"));
	}

	private Pocket createPocket(ResultSet rs) throws SQLException  {
		return new Pocket(rs.getString("struct_id"), rs.getDouble("energy"), rs.getDouble("energy_per_vol"),
				rs.getDouble("score"), getResidueList(rs, "resid"));
	}

	private Foldx createFoldx(ResultSet rs) throws SQLException  {
		return new Foldx(rs.getString("protein_acc"), (int)rs.getShort("position"), rs.getString("wild_type"),
				rs.getString("mutated_type"), rs.getDouble("foldx_ddg"), rs.getDouble("plddt"));
	}

	private Interaction createInteraction(ResultSet rs) throws SQLException  {
		return new Interaction(rs.getString("a"), getResidueList(rs, "a_residues"),
				rs.getString("b"), getResidueList(rs, "b_residues"),
				rs.getDouble("pdockq"));
	}

	private List<Integer> getResidueList(ResultSet rs, String fieldName) throws SQLException  {
		Short[] residArr = (Short[])  rs.getArray(fieldName).getArray();
		List<Integer> residList = new ArrayList<>();
		for (int i=0; i<residArr.length; ++i) {
			residList.add(residArr[i].intValue());
		}
		return residList;
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
