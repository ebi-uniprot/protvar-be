package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.grc.Crossmap;
import uk.ac.ebi.protvar.model.response.CADDPrediction;
import uk.ac.ebi.protvar.model.response.EVEScore;
import uk.ac.ebi.protvar.model.response.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.response.Variant;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class VariantsRepositoryImpl implements VariantsRepository {

	private static final String CADD_PREDICTION_TABLE = "CADD_PREDICTION_CHR";
	private static final Logger LOGGER = LoggerFactory.getLogger(VariantsRepositoryImpl.class);
	private static final String SELECT_PREDICTIONS = "select position, allele, altallele, rawscores, scores "
			+ "from TABLE_NAME where position = :position and allele = :allele and altallele = :altallele";
	
	private static final String SELECT_PREDICTIONS_BY_POSITIONS = "select chromosome, position, allele, altallele, rawscores, scores "
			+ "from CADD_PREDICTION where position in (:position)";
	
	private static Map<String, String> chromosomeQueryMap;
	private static final String[] chromosomes = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13",
			"14", "15", "16", "17", "18", "19", "20", "21", "22", "X", "Y" };

	@PostConstruct
	public void init() {
		chromosomeQueryMap = new ConcurrentHashMap<>();
		for (String chromosome : chromosomes) {
			String sql = SELECT_PREDICTIONS.replace("TABLE_NAME", CADD_PREDICTION_TABLE + chromosome);
			chromosomeQueryMap.put(chromosome, sql);
		}
	}

	private static final String SELECT_MAPPINGS_SQL = "select chromosome, protein_position, protein_seq, genomic_position, allele, codon, accession, "
			+ "reverse_strand, ensg, ensp, enst, ense, patch_name, is_match, gene_name, codon_position, is_canonical, is_mane_select, protein_name "
			+ "from genomic_protein_mapping where chromosome = :chromosome and genomic_position = :position  order by is_canonical desc";
	
	private static final String SELECT_MAPPINGS_BY_POSITION_SQL = "select chromosome, protein_position, protein_seq, genomic_position, allele, codon, accession, "
			+ "reverse_strand, ensg, ensp, enst, ense, patch_name, is_match, gene_name, codon_position, is_canonical, is_mane_select, protein_name "
			+ "from genomic_protein_mapping where genomic_position in (:position)  order by is_canonical desc";

	private static final String SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL = "select * " +
			"from genomic_protein_mapping where protein_position = :proteinPosition " +
			"and accession = :accession " +
			"and codon_position in (:codonPositions) order by is_canonical desc";

	private static final String SELECT_EVE_SCORES = "SELECT * FROM EVE_SCORE WHERE accession IN (:accessions) " +
			"AND position IN (:positions)";

	private static final String SELECT_VARIANTS = "SELECT * FROM variants WHERE id IN (:ids) ";
	private static final String SELECT_CROSSMAPS = "SELECT * FROM crossmap WHERE grch{VER}_pos IN (:pos) ";

	private NamedParameterJdbcTemplate variantJDBCTemplate;
	
	@Override
	public List<CADDPrediction> getPredictions(List<Long> positions) {
		SqlParameterSource parameters = new MapSqlParameterSource("position", positions);
		return variantJDBCTemplate.query(SELECT_PREDICTIONS_BY_POSITIONS, parameters, (rs, rowNum) -> createPrediction(rs));
	}

	private CADDPrediction createPrediction(ResultSet rs) throws SQLException {
		return new CADDPrediction(rs.getString("chromosome"), rs.getLong("position"), rs.getString("allele"),
				rs.getString("altallele"), rs.getDouble("rawscores"), rs.getDouble("scores"));
	}

	@Override
	public List<GenomeToProteinMapping> getMappings(String chromosome, Long position) {
		SqlParameterSource parameters = new MapSqlParameterSource("position", position).addValue("chromosome",
				chromosome);

		return variantJDBCTemplate.query(SELECT_MAPPINGS_SQL, parameters, (rs, rowNum) -> createMapping(rs))
			.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	private GenomeToProteinMapping createMapping(ResultSet rs) throws SQLException {
		return new GenomeToProteinMapping(rs.getString("chromosome"), rs.getLong("genomic_position"),
				rs.getInt("protein_position"), rs.getString("allele"), rs.getString("protein_seq"),
				rs.getString("codon"), rs.getString("accession"), rs.getString("ensg"), rs.getString("ensp"),
				rs.getString("enst"), rs.getString("ense"), rs.getBoolean("reverse_strand"), rs.getBoolean("is_match"),
				rs.getString("patch_name"), rs.getString("gene_name"), rs.getInt("codon_position"),
				rs.getBoolean("is_canonical"), rs.getBoolean("is_mane_select"), rs.getString("protein_name"));
	}

	@Override
	public List<GenomeToProteinMapping> getMappings(List<Long> positions) {
		SqlParameterSource parameters = new MapSqlParameterSource("position", positions);

		return variantJDBCTemplate.query(SELECT_MAPPINGS_BY_POSITION_SQL, parameters, (rs, rowNum) -> createMapping(rs))
			.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}
	public List<GenomeToProteinMapping> getMappings(String accession, Long proteinPosition, Set<Integer> codonPositions) {
		SqlParameterSource parameters = new MapSqlParameterSource("accession", accession)
				.addValue("proteinPosition", proteinPosition)
				.addValue("codonPositions", codonPositions);

		return variantJDBCTemplate.query(SELECT_MAPPING_BY_ACCESSION_AND_POSITIONS_SQL, parameters, (rs, rowNum) -> createMapping(rs))
				.stream().filter(gm -> Objects.nonNull(gm.getCodon())).collect(Collectors.toList());
	}

	public List<EVEScore> getEVEScores(List<String> accessions, List<Integer> positions) {
		if (accessions.isEmpty() || positions.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("accessions", accessions)
				.addValue("positions", positions);
		return variantJDBCTemplate.query(SELECT_EVE_SCORES, parameters, (rs, rowNum) -> createEveScore(rs));
	}

	@Override
	public List<Variant> getVariants(List<String> ids) {
		if (ids.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("ids", ids);
		return variantJDBCTemplate.query(SELECT_VARIANTS, parameters, (rs, rowNum) ->
				new Variant(rs.getString("chr"), rs.getLong("pos"), rs.getString("id"),
						rs.getString("ref"),rs.getString("alt")));
	}

	public List<Crossmap> getCrossmaps(List<Long> positions, String from) {
		if (positions.isEmpty())
			return new ArrayList<>();
		String sql = SELECT_CROSSMAPS.replace("{VER}", from);
		SqlParameterSource parameters = new MapSqlParameterSource("pos", positions);
		return variantJDBCTemplate.query(sql, parameters, (rs, rowNum) ->
				new Crossmap(rs.getString("chr"), rs.getLong("grch38_pos"), rs.getString("grch38_base"),
						rs.getLong("grch37_pos"),rs.getString("grch37_base")));
	}

	private EVEScore createEveScore(ResultSet rs) throws SQLException {
		return new EVEScore(rs.getString("accession"), rs.getInt("position"), rs.getString("wt_aa"),
				rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
	}

}
