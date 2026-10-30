package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.model.data.Protein;
import uk.ac.ebi.protvar.model.data.EnsemblGene;
import uk.ac.ebi.protvar.model.data.EnsemblTranscript;
import uk.ac.ebi.protvar.cache.ProteinCache;
import uk.ac.ebi.protvar.cache.EnsemblGeneCache;
import uk.ac.ebi.protvar.cache.EnsemblTranscriptCache;
import uk.ac.ebi.protvar.types.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MappingRepo {

	private static final Logger LOGGER = LoggerFactory.getLogger(MappingRepo.class);
	// Slim mapping (Round B): is_canonical no longer on the mapping — canonical-first ordering is done
	// app-side after cache enrichment (see createMapping). SELECT * returns the slim columns.
	private static final String MAPPINGS_IN_CHR_POS = """
   			SELECT * FROM %s
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos)
   			ON t.chr=chromosome AND t.pos=genomic_position
   			""";

	private static final String MAPPINGS_WITH_UNNEST = """
			SELECT m.* FROM %s m
			INNER JOIN (
			  SELECT UNNEST(:chromosomes) as chr, UNNEST(:positions) as pos
			) coord_list ON coord_list.chr = m.chromosome
			  AND coord_list.pos = m.genomic_position
			""";
	private static final String MAPPINGS_IN_ACC_POS = """
			SELECT
				chromosome, genomic_position, base_nucleotide,
				accession, protein_position, amino_acid,
				codon, codon_position, reverse_strand, is_match, enst, enstv, ense
			FROM %s
			INNER JOIN (VALUES :accPosList) as t(acc,pos)
			ON t.acc=accession AND t.pos=protein_position
			""";

	private final NamedParameterJdbcTemplate jdbcTemplate; // injected via constructor
	// Round B dim caches — enrich the slim mapping rows with fields it no longer carries.
	private final ProteinCache proteinCache;
	private final EnsemblGeneCache ensemblGeneCache;
	private final EnsemblTranscriptCache ensemblTranscriptCache;

	@Value("${tbl.mapping}")
	private String mappingTable; // slim mapping — used by the point/list methods (createMapping + cache enrich)
	// Enriched view (slim ⋈ protein ⋈ transcript) — used ONLY by the bulk getGenomicVariantsForInput so its
	// legacy column names (allele/protein_seq/is_canonical/reverse_strand/gene_name/ensp…) resolve unchanged.







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
	// select * from <tbl.genomic_score>
	// where chromosome='21'
	// and position=26170620; -- should return 3 rows but returning 9!
	// -- duplicates! using distinct fixes it but issue needs to be
	// -- addressed at source (import) and chr-pos-allele-alt need
	// -- to be all PKs (no need to save allele in tbl).

	/**
	 * Mapped canonical accessions. Round B: served from the protein dim cache (its canonical subset)
	 * instead of a DISTINCT scan over the 266M-row mapping table.
	 */
	public List<String> getMappedAccessions() {
		return new ArrayList<>(proteinCache.getCanonicalAccessions());
	}

	public List<GenomeToProteinMapping> getMappingsByChrPos(List<Object[]> chrPosList) {
		if (chrPosList == null || chrPosList.isEmpty())
			return List.of();
		SqlParameterSource parameters = new MapSqlParameterSource("chrPosList", chrPosList);

		return codonFirst(jdbcTemplate.query(String.format(MAPPINGS_IN_CHR_POS, mappingTable), parameters, (rs, rowNum) -> createMapping(rs)));
	}

	public List<GenomeToProteinMapping> getMappingsByChrPos(String[] chromosomes, Integer[] positions) {
		if (chromosomes == null || chromosomes.length == 0)
			return List.of();

		MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("chromosomes", chromosomes)
				.addValue("positions", positions);

		return codonFirst(jdbcTemplate.query(String.format(MAPPINGS_WITH_UNNEST, mappingTable), parameters, (rs, rowNum) -> createMapping(rs)));
	}

	/**
	 * Builds a GenomeToProteinMapping from a SLIM mapping row, enriching the columns moved out to the
	 * Round B dim tables from the in-memory caches: protein (gene_name / protein_name / is_canonical),
	 * ensembl_transcript (ensp / is_mane_select / ensg), ensembl_gene (reverse_strand / ensg version).
	 * patch_name is dropped (was unused). Null-safe on cache misses.
	 */
	private GenomeToProteinMapping createMapping(ResultSet rs) throws SQLException {
		String accession = rs.getString("accession");
		String enst = rs.getString("enst");
		String enstv = rs.getString("enstv");

		Protein protein = proteinCache.get(accession);
		EnsemblTranscript transcript = ensemblTranscriptCache.get(accession, enst);
		EnsemblGene gene = transcript != null ? ensemblGeneCache.get(transcript.getEnsg()) : null;

		// Reference base from the codon, not base_nucleotide: on 2026_02 some reverse-strand rows store the
		// complemented (transcript-strand) base and some none (~0.28% of rows, 457 accessions); the
		// codon-derived base matches the genome (CADD reference). base_nucleotide only when no usable codon.
		String codon = rs.getString("codon");
		int codonPosition = rs.getInt("codon_position");
		String ref = Codon.genomicBase(codon, codonPosition, rs.getBoolean("reverse_strand"));

		return GenomeToProteinMapping.builder()
				.chromosome(rs.getString("chromosome"))
				.genomeLocation(rs.getInt("genomic_position"))
				.isoformPosition(rs.getInt("protein_position"))
				.baseNucleotide(ref != null ? ref : rs.getString("base_nucleotide"))
				.aa(rs.getString("amino_acid"))
				.codon(codon)
				.codonPosition(codonPosition)
				.isValidRecord(rs.getBoolean("is_match"))
				.accession(accession)
				.enst(ensXVersion(enst, enstv))
				.ense(rs.getString("ense"))
				// ensembl_transcript dim
				.ensp(transcript != null ? ensXVersion(transcript.getEnsp(), transcript.getEnspv()) : null)
				.isManeSelect(transcript != null && transcript.isManeSelect())
				// ensembl_gene dim (ensg version lives on the gene record)
				.ensg(transcript != null ? ensXVersion(transcript.getEnsg(), gene != null ? gene.getEnsgv() : null) : null)
				.reverseStrand(gene != null && gene.isReverseStrand())
				// protein dim
				.isCanonical(protein != null && protein.isCanonical())
				.geneName(protein != null ? protein.getGeneName() : null)
				.proteinName(protein != null ? protein.getProteinName() : null)
				.build();
	}

	private String ensXVersion(String ens, String ver) {
		return (ens == null ? "" : ens) + "." + (ver == null ? "" : ver);
	}

	public List<GenomeToProteinMapping> getMappingsByAccPos(List<Object[]> accPosList) {
		if (accPosList == null || accPosList.isEmpty())
			return List.of();
		SqlParameterSource parameters = new MapSqlParameterSource("accPosList", accPosList);

		return codonFirst(jdbcTemplate.query(String.format(MAPPINGS_IN_ACC_POS, mappingTable), parameters, (rs, rowNum) -> createMapping(rs)));
	}

	/**
	 * Rows without a codon are kept (a real mapping; shown with PV-113, change unknown) but ordered after the rest,
	 * keeping the original order otherwise: callers that take a position's first row (reference base,
	 * representative) get one with a codon whenever there is one.
	 */
	private static List<GenomeToProteinMapping> codonFirst(List<GenomeToProteinMapping> mappings) {
		return mappings.stream().sorted(Comparator.comparing(gm -> gm.getCodon() == null)).collect(Collectors.toList());
	}

}
