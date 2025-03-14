package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.model.data.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@AllArgsConstructor
public class MappingRepo {

	@Value("${tbl.mapping}")
	private String mappingTable;

	private static final String MAPPINGS_IN_CHR_POS = """
   			SELECT * FROM %s 
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos) 
   			ON t.chr=chromosome AND t.pos=genomic_position 
   			ORDER BY is_canonical DESC
   			""";
	private static final String MAPPINGS_IN_ACC_POS = """
			SELECT
				chromosome, genomic_position, allele, 
				accession, protein_position, protein_seq, 
				codon, codon_position, reverse_strand
			FROM %s 
			INNER JOIN (VALUES :accPosList) as t(acc,pos) 
			ON t.acc=accession AND t.pos=protein_position
			""";
	private NamedParameterJdbcTemplate jdbcTemplate;

	// TODO: all SQL FROM SHOULD USE TABLE NAME FROM APP PROPERTIES
	//   NO TABLE NAME SHOULD BE HARDCODED

	// TODO CHECK - Joining eve (or other genomic-based) score
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
	// and g2p.allele = cadd.reference_allele
	// and g2p.altallele = cadd.alt_allele);
	//
	// select * from <tbl.cadd>
	// where chromosome='21'
	// and position=26170620; -- should return 3 rows but returning 9!
	// -- duplicates! using distinct fixes it but issue needs to be
	// -- addressed at source (import) and chr-pos-allele-alt need
	// -- to be all PKs (no need to save allele in tbl).

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

	public Page<UserInput> getGenInputsByEnsemblID(String id, Pageable pageable) {
		// Pre-condition: ensemblID will have been validated (using EnsemblIDValidator)

		String ensemblID = id;

		// Determine if there is a version suffix
		String version = null;
		if (ensemblID.contains(".")) {
			// Extracts the version number without the "."
			version = ensemblID.substring(ensemblID.lastIndexOf(".") + 1);
			ensemblID = ensemblID.substring(0, ensemblID.lastIndexOf("."));
		}

		// Get the column name based on Ensembl ID prefix
		String column = ensemblID.substring(0, 4).toLowerCase(); // "ensg", "enst", "ensp", "ense"
		String condition = column + " = :id";

		if (version != null) {
			// If version suffix is present, add the version column condition
			condition += " AND " + column + "v = :ver";
		}

		String rowCountSql = String.format("""
			SELECT COUNT(DISTINCT (chromosome, genomic_position, allele, protein_position)) 
			AS row_count 
			FROM %s 
			WHERE %s
        """, mappingTable, condition);

		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("id", ensemblID);
		if (version != null) {
			parameters.addValue("ver", version);
		}

		int total = jdbcTemplate.queryForObject(rowCountSql, parameters, Integer.class);

		String querySql = String.format("""
    		SELECT DISTINCT chromosome, genomic_position, allele, protein_position 
    		FROM %s 
    		WHERE %s
    		ORDER BY protein_position 
    		LIMIT %d OFFSET %d
    		""", mappingTable, condition, pageable.getPageSize(), pageable.getOffset());

		List<UserInput> genomicInputs =
				jdbcTemplate.query(querySql, parameters,
						(rs, rowNum) -> new GenomicInput(id, rs.getString("chromosome"), rs.getInt("genomic_position"), rs.getString("allele"))
				);

		return new PageImpl<>(genomicInputs, pageable, total);
	}

	/**
	 * Unpaged - used for protein download
	 * @param accession
	 * @return
	 */
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

	public List<GenomeToProteinMapping> getMappingsByChrPos(List<Object[]> chrPosList) {
		if (chrPosList == null || chrPosList.isEmpty())
			return List.of();
		SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);

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

	public List<GenomeToProteinMapping> getMappingsByAccPos(List<Object[]> accPosList) {
		if (accPosList == null || accPosList.isEmpty())
			return List.of();
		SqlParameterSource parameters = new MapSqlParameterSource("accPosList", accPosList);

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
}
