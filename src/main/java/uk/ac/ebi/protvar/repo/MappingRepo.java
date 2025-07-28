package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;
import uk.ac.ebi.protvar.types.StabilityChange;
import uk.ac.ebi.protvar.types.InputType;
import uk.ac.ebi.protvar.utils.InputTypeResolver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MappingRepo {

	private static final Logger LOGGER = LoggerFactory.getLogger(MappingRepo.class);
	private static final String MAPPINGS_IN_CHR_POS = """
   			SELECT * FROM %s
   			INNER JOIN (VALUES :chrPosList) AS t(chr,pos) 
   			ON t.chr=chromosome AND t.pos=genomic_position 
   			ORDER BY is_canonical DESC
   			""";

	private static final String MAPPINGS_WITH_UNNEST = """
			SELECT m.* FROM %s m
			INNER JOIN (
			  SELECT UNNEST(:chromosomes) as chr, UNNEST(:positions) as pos
			) coord_list ON coord_list.chr = m.chromosome
			  AND coord_list.pos = m.genomic_position
			ORDER BY m.is_canonical DESC
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

	private final NamedParameterJdbcTemplate jdbcTemplate; // injected via constructor

	@Value("${tbl.mapping}")
	private String mappingTable; // injected via Spring after constructor
	@Value("${tbl.cadd}")
	private String caddTable;
	@Value("${tbl.am}")
	private String amTable;
	@Value("${tbl.ann.str}")
	private String structureTable;
	@Value("${tbl.uprefseq}")
	private String uniprotRefseqTable;

	@Value("${tbl.dbsnp}")
	private String dbsnpTable;

	@Value("${tbl.pocket.v2}")
	private String pocketTable;

	@Value("${tbl.interaction}")
	private String interactionTable;

	@Value("${tbl.foldx}")
	private String foldxTable;

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

	public Page<VariantInput> getGenInputsByAccession(String accession, Pageable pageable) {
		String rowCountSql = String.format("""
    		SELECT COUNT(DISTINCT (chromosome, genomic_position, allele, protein_position)) 
				AS row_count 
			FROM %s 
			WHERE accession = :acc
			""", mappingTable);

		SqlParameterSource parameters = new MapSqlParameterSource("acc", accession);
		long total = jdbcTemplate.queryForObject(rowCountSql, parameters, Long.class);

		String querySql = String.format("""
    		SELECT DISTINCT chromosome, genomic_position, allele, protein_position from %s 
    		WHERE accession = :acc 
    		ORDER BY protein_position 
    		LIMIT %d OFFSET %d
    		""", mappingTable, pageable.getPageSize(), pageable.getOffset());

		SqlParameterSource queryParameters = new MapSqlParameterSource("acc", accession);

		List<VariantInput> genomicInputs =
				jdbcTemplate.query(querySql, queryParameters,
						(rs, rowNum) -> new GenomicInput(accession, rs.getString("chromosome"), rs.getInt("genomic_position"), rs.getString("allele"))
				);

		return new PageImpl<>(genomicInputs, pageable, total);
	}

	/**
	 * The mapping table does **not** contain an `alt_allele` column, so alternate alleles
	 * must be generated programmatically from the `allele` (reference base).
	 * Although this logic can also be implemented on the database side, it tends to be more complex—
	 * especially when filters are involved. See the corresponding method where database-side
	 * generation is used along with filtering for an example.
	 */
	public Page<VariantInput> getGenInputsByAccession_x3(String accession, Pageable pageable) {

		String fields = "chromosome, genomic_position, allele, protein_position, codon_position";
		String rowCountSql = String.format("""
    		SELECT COUNT(DISTINCT (%s))
			FROM %s 
			WHERE accession = :acc
			""", fields, mappingTable);

		SqlParameterSource parameters = new MapSqlParameterSource("acc", accession);
		long baseRowCount = jdbcTemplate.queryForObject(rowCountSql, parameters, Long.class);

		String querySql = String.format("""
    		SELECT DISTINCT %s 
    		FROM %s 
    		WHERE accession = :acc 
    		ORDER BY protein_position, codon_position
    		LIMIT %d OFFSET %d
    		""", fields, mappingTable, pageable.getPageSize(), pageable.getOffset());

		SqlParameterSource queryParameters = new MapSqlParameterSource("acc", accession);

		List<VariantInput> genomicInputs = new ArrayList<>();
		jdbcTemplate.query(querySql, queryParameters, (rs) -> {
			String chr = rs.getString("chromosome");
			int pos = rs.getInt("genomic_position");
			String ref = rs.getString("allele");

			for (String alt : List.of("A", "T", "G", "C")) {
				if (!alt.equalsIgnoreCase(ref)) {
					genomicInputs.add(new GenomicInput(
							String.format("%s %d %s %s", chr, pos, ref, alt),
							chr, pos, ref, alt
					));
				}
			}
		});
		// Total now reflects all expanded alt alleles
		long total = baseRowCount * 3;

		return new PageImpl<>(genomicInputs, pageable, total);
	}

	/**
	 * TODO rename to getGenCoordsForAccession
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

	public List<GenomeToProteinMapping> getMappingsByChrPos(String[] chromosomes, Integer[] positions) {
		if (chromosomes == null || chromosomes.length == 0)
			return List.of();

		MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("chromosomes", chromosomes)
				.addValue("positions", positions);

		return jdbcTemplate.query(String.format(MAPPINGS_WITH_UNNEST, mappingTable), parameters, (rs, rowNum) -> createMapping(rs))
				.stream()
				.filter(gm -> Objects.nonNull(gm.getCodon()))
				.collect(Collectors.toList());
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

	String CTE_BASED_QUERY = """
			WITH base_alleles AS (
			    SELECT ARRAY['A', 'T', 'G', 'C'] AS alleles
			),
			mapping_with_variants AS (
			    SELECT
			        m.chromosome, m.genomic_position, m.allele AS ref, alt.alt_allele
			    FROM rel_2025_01_genomic_protein_mapping m,
			        base_alleles b,
			        LATERAL unnest(ARRAY_REMOVE(b.alleles, m.allele::text)) AS alt(alt_allele)
			    WHERE m.accession = 'P22304'
			)
			SELECT count(*)
			FROM mapping_with_variants m;
			""";

	String CROSS_JOIN_QUERY = """
			SELECT chromosome, genomic_position, allele AS ref, alt
			FROM rel_2025_01_genomic_protein_mapping,
				 (VALUES ('A'), ('T'), ('G'), ('C')) AS alts(alt)
			WHERE accession = 'P22304'
			  AND alt <> allele
			""";

	// The above two queries are functionally equivalent, but the CTE-based query is more readable and maintainable.
	// Recommendation:
	// If performance is critical and this query runs often or on large datasets, go with the simpler VALUES + CROSS JOIN version.

	public Page<VariantInput> getGenInputsByAccession_CTE(String accession,
														  List<CaddCategory> caddCategories, List<AmClass> amClasses,
														  String sort, String order, Pageable pageable) {
		boolean filterByCadd = caddCategories != null
				&& !caddCategories.isEmpty();
		// handled (normalized) in controller
		//&& !EnumSet.copyOf(caddCategories).equals(EnumSet.allOf(CaddCategory.class));
		boolean filterByAm = amClasses != null
				&& !amClasses.isEmpty();
		//&& !EnumSet.copyOf(amClasses).equals(EnumSet.allOf(AmClass.class));
		boolean sortByCadd = "CADD".equalsIgnoreCase(sort);
		boolean sortByAm = "AM".equalsIgnoreCase(sort);

		boolean joinCadd = filterByCadd || sortByCadd;
		boolean joinAm = filterByAm || sortByAm;

		String sortOrder = "ASC".equalsIgnoreCase(order) ? "ASC" : "DESC";
		String fields = "m.chromosome, m.genomic_position, m.allele, m.alt_allele, m.protein_position, m.codon_position";
		String baseQuery = """
			WITH base_alleles AS (
				SELECT ARRAY['A', 'T', 'G', 'C'] AS alleles
			),
			mapping_with_variants AS (
				SELECT
					m.chromosome, m.genomic_position, m.allele, m.accession, m.protein_position, m.codon_position, m.protein_seq,
					alt.alt_allele,
					CASE
						WHEN m.codon_position = 1 THEN rna_base_for_strand(alt.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
						WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(alt.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
						WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(alt.alt_allele, m.reverse_strand)
						ELSE m.codon
					END AS alt_codon
				FROM %s m,
					base_alleles b,
					LATERAL unnest(ARRAY_REMOVE(b.alleles, m.allele::text)) AS alt(alt_allele)
				WHERE m.accession = :accession
			)
			SELECT %s
			FROM mapping_with_variants m
			LEFT JOIN codon_table c ON c.codon = upper(m.alt_codon)
		""";
		StringBuilder sql = new StringBuilder(baseQuery.replaceFirst("%s", mappingTable));

		// Conditional joins
		if (joinCadd) {
			sql.append(String.format("""
				LEFT JOIN %s cadd ON
					cadd.chromosome = m.chromosome AND
					cadd.position = m.genomic_position AND
					cadd.reference_allele = m.allele AND
					cadd.alt_allele = m.alt_allele
			""", caddTable));
		}

		if (joinAm) {
			sql.append(String.format("""
				LEFT JOIN %s am ON
					am.accession = m.accession AND
					am.position = m.protein_position AND
					am.wt_aa = m.protein_seq AND
					am.mt_aa = c.amino_acid
			""", amTable));
		}

		sql.append(" WHERE 1=1");

		MapSqlParameterSource parameters = new MapSqlParameterSource();
		parameters.addValue("accession", accession);

		// Conditional filters
		if (filterByCadd) {
			List<String> caddClauses = new ArrayList<>();
			for (int i = 0; i < caddCategories.size(); i++) {
				CaddCategory category = caddCategories.get(i);
				String minParam = "caddMin" + i;
				String maxParam = "caddMax" + i;
				caddClauses.add("(cadd.score >= :" + minParam + " AND cadd.score < :" + maxParam + ")");
				parameters.addValue(minParam, category.getMin());
				parameters.addValue(maxParam, category.getMax());
			}
			sql.append(" AND (").append(String.join(" OR ", caddClauses)).append(")");
		}

		if (filterByAm) {
			List<Integer> amValues = amClasses.stream().map(AmClass::getValue).toList();
			sql.append(" AND (am.am_class IN (:amClasses)  OR am.am_class IS NULL)");
			parameters.addValue("amClasses", amValues);
		}

		// Count query
		String countSql = String.format(sql.toString(), "COUNT(DISTINCT (" + fields + "))");
		long total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);

		// Sorting
		sql.append(" ORDER BY ");
		if ("CADD".equalsIgnoreCase(sort)) {
			fields += ", cadd.score ";
			sql.append("cadd.score ").append(sortOrder).append(", ");
		} else if ("AM".equalsIgnoreCase(sort)) {
			fields += ", am.am_pathogenicity ";
			sql.append("am.am_pathogenicity ").append(sortOrder).append(", ");
		}
		sql.append("m.protein_position, m.codon_position");

		// Pagination
		sql.append(" LIMIT :pageSize OFFSET :offset");
		parameters.addValue("pageSize", pageable.getPageSize());
		parameters.addValue("offset", pageable.getOffset());

		List<VariantInput> genomicInputs = jdbcTemplate.query(
				String.format(sql.toString(), "DISTINCT " + fields),
				parameters,
				(rs, rowNum) -> {
					String chr = rs.getString("chromosome");
					int pos = rs.getInt("genomic_position");
					String ref = rs.getString("allele");
					String alt = rs.getString("alt_allele");
					return new GenomicInput(String.format("%s %d %s %s", chr, pos, ref, alt), chr, pos, ref, alt);
				}
		);
		return new PageImpl<>(genomicInputs, pageable, total);
	}

	// TODO If not already in place, create indexes on:
	//	rel_2025_01_genomic_protein_mapping(accession, protein_position)
	//	cadd_table(chromosome, position, ref, alt)
	//	alphamissense_table(accession, position, ref, alt)

	public Page<VariantInput> getGenomicVariantsForInput(MappingRequest request, Pageable pageable) {
		if (pageable == null) {
			LOGGER.warn("Defaulting to page {}, size {}.", PageUtils.DEFAULT_PAGE, PageUtils.DEFAULT_PAGE_SIZE);
			pageable = PageRequest.of(PageUtils.DEFAULT_PAGE, PageUtils.DEFAULT_PAGE_SIZE);
		}
		boolean isDownload = request instanceof DownloadRequest; // no need to run countQuery for download

		// Determine filtering and sorting requirements
		boolean filterByCadd = isFilteringRequired(request.getCadd(), CaddCategory.class);
		boolean filterByAm = isFilteringRequired(request.getAm(), AmClass.class);
		boolean filterKnown = Boolean.TRUE.equals(request.getKnown());
		boolean filterPocket = Boolean.TRUE.equals(request.getPocket());
		boolean filterInteract = Boolean.TRUE.equals(request.getInteract());
		boolean filterStability = request.getStability() != null && !request.getStability().isEmpty();
		boolean sortByCadd = "CADD".equalsIgnoreCase(request.getSort());
		boolean sortByAm = "AM".equalsIgnoreCase(request.getSort());

		boolean joinCadd = filterByCadd || sortByCadd;
		boolean joinAm = filterByAm || sortByAm;
		boolean joinCodonTable = joinAm || filterStability;

		String sortOrder = "ASC".equalsIgnoreCase(request.getOrder()) ? "ASC" : "DESC";
		String fields = "m.chromosome, m.genomic_position, m.allele, m.alt_allele, m.protein_position, m.codon_position";

		// Build the base query
		String dbsnpJoin = buildDbsnpJoin(filterKnown);
		MapSqlParameterSource parameters = new MapSqlParameterSource();
		String inputCondition = buildInputCondition(request.getInput(), request.getType(), parameters);

		String baseQuery = """
					WITH mapping_with_variants AS (
						SELECT
							m.chromosome, m.genomic_position, m.allele, m.accession,
							m.protein_position, m.codon_position, m.protein_seq,
							m.codon, m.reverse_strand,
							alt.alt_allele
						FROM %s m
						JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alt(alt_allele) ON TRUE
						%s -- conditional join (e.g. dbsnp)
						%s -- input related join/where condition
					)
					SELECT %s
					FROM mapping_with_variants m
				""";

		StringBuilder sql = new StringBuilder(baseQuery
				.replaceFirst("%s", mappingTable)
				.replaceFirst("%s", dbsnpJoin)
				.replaceFirst("%s", inputCondition));


		// Add conditional joins
		if (joinCadd) {
			String joinType = filterByCadd ? "INNER" : "LEFT";
			sql.append(String.format("""
				%s JOIN %s cadd ON
					cadd.chromosome = m.chromosome AND
					cadd.position = m.genomic_position AND
					cadd.reference_allele = m.allele AND
					cadd.alt_allele = m.alt_allele
			""", joinType, caddTable));
		}

		if (joinCodonTable) { // amino acid level filter
			sql.append("""
    			LEFT JOIN codon_table c ON c.codon = upper(CASE
					WHEN m.codon_position = 1 THEN rna_base_for_strand(m.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
					WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(m.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
					WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(m.alt_allele, m.reverse_strand)
					ELSE m.codon
				END)
			""");
		}

		if (joinAm) {
			String joinType = filterByAm ? "INNER" : "LEFT";
			sql.append(String.format("""
				%s JOIN %s am ON
					am.accession = m.accession AND
					am.position = m.protein_position AND
					am.wt_aa = m.protein_seq AND -- this may be removed
					am.mt_aa = c.amino_acid
			""", joinType, amTable));
		}

		if (filterPocket) {
			sql.append(String.format("""
				INNER JOIN %s p ON
					p.struct_id = m.accession AND
					m.protein_position = ANY(p.pocket_resid)
			""", pocketTable));
		}

		if (filterInteract) {
			sql.append(String.format("""
				INNER JOIN %s i ON
					(i.a = m.accession AND m.protein_position = ANY(i.a_residues)) OR 
					(i.b = m.accession AND m.protein_position = ANY(i.b_residues))
			""", interactionTable));
		}

		if (filterStability) {
			sql.append(String.format("""
					INNER JOIN %s f ON
						f.protein_acc = m.accession AND
						f.position = m.protein_position AND
						f.mutated_type = c.amino_acid
					""", foldxTable));
		}

		sql.append(" WHERE 1=1");

		// Add filters
		if (filterByCadd) {
			List<String> caddClauses = new ArrayList<>();
			for (int i = 0; i < request.getCadd().size(); i++) {
				CaddCategory category = request.getCadd().get(i);
				String minParam = "caddMin" + i;
				String maxParam = "caddMax" + i;
				caddClauses.add("(cadd.score >= :" + minParam + " AND cadd.score < :" + maxParam + ")");
				parameters.addValue(minParam, category.getMin());
				parameters.addValue(maxParam, category.getMax());
			}
			sql.append(" AND (").append(String.join(" OR ", caddClauses)).append(")");
		}

		if (filterByAm) {
			List<Integer> amValues = request.getAm().stream().map(AmClass::getValue).toList();
			sql.append(" AND am.am_class IN (:amClasses)");
			parameters.addValue("amClasses", amValues);
		}

		if (filterStability) {
			Set<StabilityChange> changes = EnumSet.copyOf(request.getStability());
			if (changes.equals(EnumSet.allOf(StabilityChange.class))) {
			 // inner join is all that's needed
			} else {
				if (changes.contains(StabilityChange.LIKELY_DESTABILISING)) {
					sql.append(" AND f.foldx_ddg >= 2");
				} else if (changes.contains(StabilityChange.UNLIKELY_DESTABILISING)) {
					sql.append(" AND f.foldx_ddg < 2");
				}
			}
		}

		long total = -1;
		if (!isDownload) {
			String countSql = String.format(sql.toString(), "COUNT(DISTINCT (" + fields + "))");
			total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);

			// Early return if no results
			if (total == 0) {
				return Page.empty(pageable);
			}
		}

		// Sorting
		sql.append(" ORDER BY ");
		if (sortByCadd) {
			fields += ", cadd.score ";
			sql.append("cadd.score ").append(sortOrder).append(", ");
		} else if (sortByAm) {
			fields += ", am.am_pathogenicity ";
			sql.append("am.am_pathogenicity ").append(sortOrder).append(", ");
		}
		sql.append("m.protein_position, m.codon_position, m.alt_allele"); // consider removing alt_allele?

		// Pagination
		sql.append(" LIMIT :pageSize OFFSET :offset");
		parameters.addValue("pageSize", pageable.getPageSize());
		parameters.addValue("offset", pageable.getOffset());

		// Execute query
		List<VariantInput> variants = jdbcTemplate.query(
				String.format(sql.toString(), "DISTINCT " + fields),
				parameters,
				(rs, rowNum) -> {
					String chr = rs.getString("chromosome");
					int pos = rs.getInt("genomic_position");
					String ref = rs.getString("allele");
					String alt = rs.getString("alt_allele");
					return new GenomicInput(String.format("%s %d %s %s", chr, pos, ref, alt), chr, pos, ref, alt);
			}
		);
		return isDownload ?
				new PageImpl<>(variants) : // unpaged, total count not needed
		        new PageImpl<>(variants, pageable, total);
	}

	private <T extends Enum<T>> boolean isFilteringRequired(List<T> categories, Class<T> enumClass) {
		return categories != null
				&& !categories.isEmpty()
				&& !EnumSet.copyOf(categories).equals(EnumSet.allOf(enumClass));
	}

	private String buildDbsnpJoin(boolean filterKnown) {
		// TODO consider normalising alt for full index on join incl. alt col
		// dbsnp index:
		//CREATE INDEX dbsnp_b156_chr_pos_ref_idx ON dbsnp_b156 (chr, pos, ref);
		if (filterKnown) {
			return String.format("""
            JOIN %s d ON d.chr = m.chromosome
                AND d.pos = m.genomic_position
                AND d.ref = m.allele
                AND alt.alt_allele = ANY(string_to_array(d.alt, ','))
        """, dbsnpTable);
		}
		return "";
	}

	private String buildInputCondition(String input, InputType inputType, MapSqlParameterSource parameters) {
		if (input == null || input.isBlank() || inputType == null) {
			// No input provided - this is valid, return all variants
			return "WHERE alt.alt_allele <> m.allele";
		}

		return switch (inputType) {
			case ENSEMBL -> buildEnsemblCondition(input, parameters);
			case UNIPROT -> {
				parameters.addValue("input", input);
				yield "WHERE m.accession = :input AND alt.alt_allele <> m.allele";
			}
			case PDB -> {
				// PDB is stored in lowercase in the db table
				// Using LOWER on the right side only to ensure index on pdb_id is used
				parameters.addValue("input", input);
				yield String.format("""
                JOIN (
                    SELECT DISTINCT accession, unp_start, unp_end
                    FROM %s
                    WHERE pdb_id = LOWER(:input)
                ) s ON m.accession = s.accession
                WHERE m.protein_position BETWEEN s.unp_start AND s.unp_end
                AND alt.alt_allele <> m.allele
            """, structureTable);
			}
			case REFSEQ -> buildRefseqCondition(input, parameters);
			case GENE -> {
				parameters.addValue("input", input);
				yield "WHERE m.gene_name = :input AND alt.alt_allele <> m.allele";
			}
			default -> // Unknown input type - treat as no input
					"WHERE alt.alt_allele <> m.allele";
		};
	}

	private String buildEnsemblCondition(String input, MapSqlParameterSource parameters) {
		Matcher matcher = Pattern.compile("^(ENS[GTPE])(\\d{11})(\\.\\d+)?$", Pattern.CASE_INSENSITIVE).matcher(input);
		if (!matcher.matches()) {
			// Invalid format - fall back to no input filtering
			return "WHERE alt.alt_allele <> m.allele";
			// skipping the input might indicate results for the unsupported format,
			// todo consider returning empty page instead
		}

		String prefix = matcher.group(1).toUpperCase();
		String baseId = prefix + matcher.group(2);
		String versionStr = matcher.group(3);
		String version = versionStr != null ? versionStr.substring(1) : null; // Remove the dot

		String idColumn;
		String versionColumn;
		switch (prefix) {
			case "ENSG" -> {
				idColumn = "ensg";
				versionColumn = "ensgv";
			}
			case "ENST" -> {
				idColumn = "enst";
				versionColumn = "enstv";
			}
			case "ENSP" -> {
				idColumn = "ensp";
				versionColumn = "enspv";
			}
			default -> {
				// Unsupported type - fall back to no input filtering
				return "WHERE alt.alt_allele <> m.allele";
			}
		}

		StringBuilder condition = new StringBuilder("WHERE m." + idColumn + " = :input");
		parameters.addValue("input", baseId);

		if (version != null) {
			condition.append(" AND m.").append(versionColumn).append(" = :version");
			parameters.addValue("version", version);
		}

		condition.append(" AND alt.alt_allele <> m.allele");
		return condition.toString();
	}

	private String buildRefseqCondition(String input, MapSqlParameterSource parameters) {
		Matcher matcher = InputTypeResolver.REFSEQ_REGEX.matcher(input);
		boolean hasVersion = matcher.matches() && matcher.group(2) != null;

		String whereClause = hasVersion
				? "refseq_acc = :input"
				: "refseq_acc ILIKE :input || '.%%'"; // escape % for String.format

		parameters.addValue("input", input);
		return String.format("""
        JOIN (
            SELECT DISTINCT uniprot_acc
            FROM %s
            WHERE %s
        ) r ON m.accession = r.uniprot_acc
        WHERE alt.alt_allele <> m.allele
    """, uniprotRefseqTable, whereClause);
	}

}
