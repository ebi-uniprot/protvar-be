package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.*;
import uk.ac.ebi.protvar.model.score.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class ProtVarDataRepoImpl implements ProtVarDataRepo {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProtVarDataRepoImpl.class);

	@Value("${tbl.mapping}")
	private String mappingTable;

	@Value("${tbl.crossmap}")
	private String crossmapTable;

	@Value("${tbl.cadd}")
	private String caddTable;


	private static final List EMPTY_RESULT = new ArrayList<>();

	private static final String CADDS_IN_CHR_POS = """
   			SELECT * FROM %s 
   			INNER JOIN (VALUES :chrPosSet) AS t(chr,pos) 
   			ON t.chr=chromosome AND t.pos=position
   			"""; // optimised from: SELECT * FROM <tbl.cadd> WHERE (chromosome,position) IN (:chrPosSet)
			// to avoid max num of "in" values reached. Refer to https://stackoverflow.com/questions/1009706/

	private static final String MAPPINGS_IN_CHR_POS = """
   			SELECT * FROM %s 
   			INNER JOIN (VALUES :chrPosSet) AS t(chr,pos) 
   			ON t.chr=chromosome AND t.pos=genomic_position 
   			ORDER BY is_canonical DESC
   			""";
	private static final String MAPPINGS_IN_ACC_POS = """
			SELECT
				chromosome, genomic_position, allele, 
				accession, protein_position, protein_seq, 
				codon, codon_position, reverse_strand
			FROM %s 
			INNER JOIN (VALUES :accPosSet) as t(acc,pos) 
			ON t.acc=accession AND t.pos=protein_position
			""";

	private static final String CROSSMAPS_IN_GRCHX_POS = """
			SELECT c.* FROM %s c 
			INNER JOIN (VALUES :pos) AS t(pos)
			ON t.pos=c.grch%s_pos
			""";

	private static final String CROSSMAPS_IN_CHR_GRCH37_POS = """
   			SELECT c.* FROM %s c 
   			INNER JOIN (VALUES :chrPos37) AS t(chr,pos)
   			ON t.chr=c.chr AND t.pos=c.grch37_pos
   			""";

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

	private static final String SELECT_FOLDXS_BY_ACC_AND_POS = """
 			SELECT * FROM afdb_foldx 
 			WHERE protein_acc=:accession AND position=:position
 			""";

	private static final String SELECT_FOLDXS_BY_ACC_AND_POS_VARIANT = """
 			SELECT * FROM afdb_foldx 
 			WHERE protein_acc=:accession 
 			AND position=:position 
 			AND mutated_type=:variantAA
 			""";

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

	//================================================================================
	// Conservation, EVE, ESM1b and AM scores
	//================================================================================
	private static final String CONSERV = """
    		select 'CONSERV' as type, null as mt_aa, score, null as class
    		from conserv_score 
    		where acc=:acc and pos=:pos
 			""";
	private static final String EVE = """
			select 'EVE' as type, mt_aa, score, class 
			from eve_score 
			where accession=:acc and position=:pos
			""";
	private static final String ESM = """
			select 'ESM' as type, mt_aa, score, null as class 
			from esm 
			where accession=:acc and position=:pos
			""";
	private static final String AM = """
			select 'AM' as type, mt_aa, am_pathogenicity as score, am_class as class 
			from alphamissense 
			where accession=:acc and position=:pos
			""";

	private static final String SCORES = """
    		select 'CONSERV' as type, acc as accession, pos as position, null as mt_aa, score, null as class
    		from conserv_score 
    		inner join (values :accPosSet) as t(_acc,_pos)
    		on t._acc=acc and t._pos=pos
			union    		
         	select 'EVE' as type, accession, position, mt_aa, score, class 
			from eve_score 
			inner join (values :accPosSet) as t(_acc,_pos)
			on t._acc=accession and t._pos=position
			union
			select 'ESM' as type, accession, position, mt_aa, score, null as class 
			from esm 
    		inner join (values :accPosSet) as t(_acc,_pos)
			on t._acc=accession and t._pos=position
			union
			select 'AM' as type, accession, position, mt_aa, am_pathogenicity as score, am_class as class 
			from alphamissense 
    		inner join (values :accPosSet) as t(_acc,_pos)
			on t._acc=accession and t._pos=position
			""";

	// TODO add Foldx to getScores
	private static final String FOLDX = """
    		select 'FOLDX' as type, mutated_type as mt_aa, foldx_ddg as score, null as class
    		from conserv_score 
    		where protein_acc=:acc and position=:pos
 			"""; // mutated_type=:mt

	private NamedParameterJdbcTemplate jdbcTemplate;

	// Joining eve (or other genomic-based) score
	// One way of doing it, basically to enable sorting at db level

	// select ARRAY_REMOVE(array['A', 'T', 'G', 'C'], 'C')
	// #
	// 1 {A,T,G}
	// select unnest(ARRAY_REMOVE(array['A', 'T', 'G', 'C'], 'C')
	// #
	// 1 A
	// 2 T
	// 3 G
	// select * from <tbl.mapping> where accession = 'P05067' and protein_position=1;
	// #	chromosome	protein_position	protein_seq	genomic_position	allele	codon	accession	reverse_strand	ensg	ensgv   ensp	enspv	enst	enstv	ense	is_match	patch_name	gene_name	codon_position	is_canonical	is_mane_select	protein_name
	// 1	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein
	// 2	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein
	// 3	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein
	//
	// select *, ARRAY_REMOVE(array['A', 'T', 'G', 'C'], allele::text)  from <tbl.mapping> where accession = 'P05067' and protein_position=1;
	// #	chromosome	protein_position	protein_seq	genomic_position	allele	codon	accession	reverse_strand	ensg	ensgv	ensp	enspv	enst	enstv	ense	is_match	patch_name	gene_name	codon_position	is_canonical	is_mane_select	protein_name	array_remove
	// 1	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	{A,G,C}
	// 2	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	{T,G,C}
	// 3	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	{A,T,G}
	//
	// select *, unnest(ARRAY_REMOVE(array['A', 'T', 'G', 'C'], allele::text)) as altallele  from <tbl.mapping> where accession = 'P05067' and protein_position=1;
	// #	chromosome	protein_position	protein_seq	genomic_position	allele	codon	accession	reverse_strand	ensg	ensgv	ensp	enspv	enst	enstv	ense	is_match	patch_name	gene_name	codon_position	is_canonical	is_mane_select	protein_name	altallele
	// 1	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	A
	// 2	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	G
	// 3	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	C
	// 4	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	T
	// 5	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	G
	// 6	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	C
	// 7	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	A
	// 8	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	T
	// 9	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	G
	// select g2p.*, cadd.*
	// from (
	// select *, unnest(ARRAY_REMOVE(array['A', 'T', 'G', 'C'], allele::text)) as altallele
	// from <tbl.mapping> g2p
	// where accession = 'P05067' and protein_position=1) g2p
	// inner join cadd_prediction cadd on (g2p.chromosome = cadd.chromosome
	// and g2p.genomic_position = cadd.position
	// and g2p.allele = cadd.allele
	// and g2p.altallele = cadd.altallele);
	//
	// select * from <tbl.cadd>
	// where chromosome='21'
	// and position=26170620; -- should return 3 rows but returning 9!
	// -- duplicates! using distinct fixes it but issue needs to be
	// -- addressed at source (import) and chr-pos-allele-alt need
	// -- to be all PKs (no need to save allele in tbl).

	@Override
	public Page<UserInput> getGenInputsByAccession(String accession, Pageable pageable) {
		String rowCountSql = String.format("""
    		SELECT COUNT(DISTINCT (chromosome, genomic_position, allele, protein_position)) 
				AS row_count 
			FROM %s 
			WHERE accession = :acc
			""", mappingTable);

		SqlParameterSource parameters = new MapSqlParameterSource("acc", accession);
		int total = jdbcTemplate.queryForObject(rowCountSql, parameters, Integer.class);

		String querySql = String.format("""
    		SELECT DISTINCT chromosome, genomic_position, allele, protein_position from %s 
    		WHERE accession = :acc 
    		ORDER BY protein_position 
    		LIMIT %d OFFSET %d
    		""", mappingTable, pageable.getPageSize(), pageable.getOffset());

		SqlParameterSource queryParameters = new MapSqlParameterSource("acc", accession);

		List<UserInput> genomicInputs =
				jdbcTemplate.query(querySql, queryParameters,
						(rs, rowNum) -> new GenomicInput(accession, rs.getString("chromosome"), rs.getInt("genomic_position"), rs.getString("allele"))
				);

		return new PageImpl<>(genomicInputs, pageable, total);
	}

	/**
	 * Unpaged - used for protein download
	 * @param accession
	 * @return
	 */
	@Override
	public List<String> getGenInputsByAccession(String accession, Integer page, Integer pageSize) {
		String querySql = String.format("""
    		SELECT DISTINCT chromosome, genomic_position, allele, protein_position from %s 
    		WHERE accession = :acc 
    		ORDER BY protein_position 
    		""", mappingTable);
		if (page != null)
			querySql += "LIMIT %d OFFSET %d".formatted(page, pageSize);

		SqlParameterSource queryParameters = new MapSqlParameterSource("acc", accession);

		List<String> genomicInputs =
				jdbcTemplate.query(querySql, queryParameters,
						(rs, rowNum) -> String.format("%s %d %s", rs.getString("chromosome"), rs.getInt("genomic_position"), rs.getString("allele"))
				);
		return genomicInputs;
	}

	@Override
	public List<CADDPrediction> getCADDByChrPos(Set<Object[]> chrPosSet) {
		if (chrPosSet == null || chrPosSet.isEmpty())
			return EMPTY_RESULT;
		SqlParameterSource parameters = new MapSqlParameterSource("chrPosSet", chrPosSet);
		return jdbcTemplate.query(String.format(CADDS_IN_CHR_POS, caddTable), parameters, (rs, rowNum) -> createPrediction(rs));
	}

	private CADDPrediction createPrediction(ResultSet rs) throws SQLException {
		return new CADDPrediction(rs.getString("chromosome"), rs.getInt("position"), rs.getString("allele"),
				rs.getString("altallele"), rs.getDouble("rawscores"), rs.getDouble("scores"));
	}

	@Override
	public List<GenomeToProteinMapping> getMappingsByChrPos(Set<Object[]> chrPosSet) {
		if (chrPosSet == null || chrPosSet.isEmpty())
			return EMPTY_RESULT;
		SqlParameterSource parameters = new MapSqlParameterSource("chrPosSet", chrPosSet);

		return jdbcTemplate.query(String.format(MAPPINGS_IN_CHR_POS, mappingTable), parameters, (rs, rowNum) -> createMapping(rs))
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
				.ensg(ensXVersion(rs.getString("ensg"), rs.getString("ensgv")))
				.ensp(ensXVersion(rs.getString("ensp"), rs.getString("enspv")))
				.enst(ensXVersion(rs.getString("enst"), rs.getString("enstv")))
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

	public List<GenomeToProteinMapping> getMappingsByAccPos(Set<Object[]> accPosSet) {
		if (accPosSet == null || accPosSet.isEmpty())
			return EMPTY_RESULT;
		SqlParameterSource parameters = new MapSqlParameterSource("accPosSet", accPosSet);

		return jdbcTemplate.query(String.format(MAPPINGS_IN_ACC_POS, mappingTable), parameters, (rs, rowNum) ->
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
		String sql = String.format("""
    		SELECT 100 * COUNT (DISTINCT (chr, grchVER_pos, grchVER_base)) / :num 
    		FROM %s
    		WHERE (chr, grchVER_pos, grchVER_base) 
    		IN (:chrPosRef)
    		""", crossmapTable);

		sql = sql.replaceAll("VER", ver);

		SqlParameterSource parameters = new MapSqlParameterSource("num", chrPosRefList.size())
				.addValue("chrPosRef", chrPosRefList);

		return jdbcTemplate.queryForObject(sql, parameters, Integer.class);
	}

	public List<Crossmap> getCrossmaps(List<Integer> positions, String grch) {
		if (positions.isEmpty())
			return new ArrayList<>();
		String sql = String.format(CROSSMAPS_IN_GRCHX_POS, crossmapTable, grch);
		SqlParameterSource parameters = new MapSqlParameterSource("pos", positions);
		return jdbcTemplate.query(sql, parameters, new BeanPropertyRowMapper<>(Crossmap.class));
	}

	public List<Crossmap> getCrossmapsByChrPos37(List<Object[]> chrPos37) {
		if (chrPos37.isEmpty())
			return new ArrayList<>();
		SqlParameterSource parameters = new MapSqlParameterSource("chrPos37", chrPos37);
		return jdbcTemplate.query(String.format(CROSSMAPS_IN_CHR_GRCH37_POS, crossmapTable),
				parameters, new BeanPropertyRowMapper<>(Crossmap.class));
	}

	//================================================================================
	// Foldxs, pockets, and protein interactions
	//================================================================================
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
		return new Pocket(rs.getString("struct_id"), rs.getInt("pocket_id"),  rs.getDouble("rad_gyration"),
				rs.getDouble("energy_per_vol"), rs.getDouble("buriedness"), getResidueList(rs, "resid"),
				rs.getDouble("mean_plddt"), rs.getDouble("score"));
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


	//================================================================================
	// Conservation, EVE, ESM1b and AM scores
	//================================================================================
	public List<Score> getScores(String acc, Integer pos, String mt, Score.Name name) {
		String sql = String.format("%s union %s union %s union %s", CONSERV, appendMt(EVE, mt), appendMt(ESM, mt), appendMt(AM, mt));
		if (name != null) {
			switch (name) {
				case CONSERV:
					sql = CONSERV;
					break;
				case EVE:
					sql = appendMt(EVE, mt);
					break;
				case ESM:
					sql = appendMt(ESM, mt);
					break;
				case AM:
					sql = appendMt(AM, mt);
					break;
			}
		}
		MapSqlParameterSource parameters = new MapSqlParameterSource("acc", acc)
				.addValue("pos", pos)
				.addValue("mt", mt);
		List results = jdbcTemplate.query(sql,
				parameters,
				(rs, rowNum) -> {
					String t = rs.getString("type");
					if (t.equalsIgnoreCase(Score.Name.CONSERV.name())) {
						return new ConservScore(null, rs.getDouble("score"));
					} else if (t.equalsIgnoreCase(Score.Name.EVE.name())) {
						return new EVEScore(rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
					} else if (t.equalsIgnoreCase(Score.Name.ESM.name())) {
						return new ESMScore(rs.getString("mt_aa"), rs.getDouble("score"));
					} else if (t.equalsIgnoreCase(Score.Name.AM.name())) {
						return new AMScore(rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
					}
					return null;
				});
		results.removeIf(Objects::isNull);
		return results;
	}

	public List<Score> getScores(Set<Object[]> accPosSet) {
		if (accPosSet.size() > 0) {
			SqlParameterSource parameters = new MapSqlParameterSource("accPosSet", accPosSet);
			List results = jdbcTemplate.query(SCORES,
					parameters,
					(rs, rowNum) -> {
						String t = rs.getString("type");
						if (t.equalsIgnoreCase(Score.Name.CONSERV.name())) {
							return new ConservScore(rs.getString("accession"), rs.getInt("position"), null, rs.getDouble("score"));
						} else if (t.equalsIgnoreCase(Score.Name.EVE.name())) {
							return new EVEScore(rs.getString("accession"), rs.getInt("position"), rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
						} else if (t.equalsIgnoreCase(Score.Name.ESM.name())) {
							return new ESMScore(rs.getString("accession"), rs.getInt("position"), rs.getString("mt_aa"), rs.getDouble("score"));
						} else if (t.equalsIgnoreCase(Score.Name.AM.name())) {
							return new AMScore(rs.getString("accession"), rs.getInt("position"), rs.getString("mt_aa"), rs.getDouble("score"), rs.getInt("class"));
						}
						return null;
					});
			results.removeIf(Objects::isNull);
			return results;
		}
		return EMPTY_RESULT;
	}

	private String appendMt(String sql, String mt) {
		if (mt == null)
			return sql;
		return sql + " and mt_aa=:mt";
	}
}
