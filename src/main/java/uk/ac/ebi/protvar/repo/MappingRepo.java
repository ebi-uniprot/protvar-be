package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.GenomeToProteinMapping;
import uk.ac.ebi.protvar.types.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class MappingRepo {

	private static final String MAPPINGS_BY_CHR_POS = """
			SELECT m.* 
			FROM %s m
			INNER JOIN (
			  SELECT UNNEST(:chromosomes) as chr, UNNEST(:positions) as pos
			) coord_list ON coord_list.chr = m.chromosome
			  AND coord_list.pos = m.genomic_position
			ORDER BY m.is_canonical DESC
			""";
	private static final String MAPPINGS_BY_ACC_POS = """
            SELECT
               m.chromosome, m.genomic_position, m.allele,
               m.accession, m.protein_position, m.protein_seq,
               m.codon, m.codon_position, m.reverse_strand
            FROM %s m
            INNER JOIN (
              SELECT UNNEST(:accessions) as acc, UNNEST(:positions) as pos
            ) acc_list ON acc_list.acc = m.accession
              AND acc_list.pos = m.protein_position
			""";

	private final NamedParameterJdbcTemplate jdbcTemplate; // injected via constructor

	@Value("${tbl.mapping}")
	private String mappingTable; // injected via Spring after constructor
	@Value("${tbl.cadd}")
	private String caddTable;
    @Value("${tbl.allelefreq}")
    private String alleleFreqTable;
    @Value("${tbl.am}")
    private String amTable;
    @Value("${tbl.popeve}")
    private String popeveTable;
    @Value("${tbl.esm}")
    private String esmTable;
    @Value("${tbl.conserv}")
    private String conservationTable;
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

    /* Expanding mapping table with alt_allele

	 select ARRAY_REMOVE(array['A', 'T', 'G', 'C'], 'C')
	 #
	 1 {A,T,G}
	 select unnest(ARRAY_REMOVE(array['A', 'T', 'G', 'C'], 'C')
	 #
	 1 A
	 2 T
	 3 G
	 select * from <tbl.mapping> where accession = 'P05067' and protein_position=1;
	 #	chromosome	protein_position	protein_seq	genomic_position	allele	codon	accession	reverse_strand	ensg	ensgv   ensp	enspv	enst	enstv	ense	is_match	patch_name	gene_name	codon_position	is_canonical	is_mane_select	protein_name
	 1	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein
	 2	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein
	 3	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein

	 select *, ARRAY_REMOVE(array['A', 'T', 'G', 'C'], allele::text)  from <tbl.mapping> where accession = 'P05067' and protein_position=1;
	 #	chromosome	protein_position	protein_seq	genomic_position	allele	codon	accession	reverse_strand	ensg	ensgv	ensp	enspv	enst	enstv	ense	is_match	patch_name	gene_name	codon_position	is_canonical	is_mane_select	protein_name	array_remove
	 1	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	{A,G,C}
	 2	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	{T,G,C}
	 3	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	{A,T,G}

	 select *, unnest(ARRAY_REMOVE(array['A', 'T', 'G', 'C'], allele::text)) as altallele  from <tbl.mapping> where accession = 'P05067' and protein_position=1;
	 #	chromosome	protein_position	protein_seq	genomic_position	allele	codon	accession	reverse_strand	ensg	ensgv	ensp	enspv	enst	enstv	ense	is_match	patch_name	gene_name	codon_position	is_canonical	is_mane_select	protein_name	altallele
	 1	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	A
	 2	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	G
	 3	21	1	M	26170620	T	Aug	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	1	true	true	Amyloid-beta precursor protein	C
	 4	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	T
	 5	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	G
	 6	21	1	M	26170619	A	aUg	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	2	true	true	Amyloid-beta precursor protein	C
	 7	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	A
	 8	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	T
	 9	21	1	M	26170618	C	auG	P05067	true	ENSG00000142192	22	ENSP00000284981	4	ENST00000346798	8	ENSE00003845466	true	Chromosome 21	APP	3	true	true	Amyloid-beta precursor protein	G
     */

	public List<GenomeToProteinMapping> getMappingsByChrPos(String[] chromosomes, Integer[] gpositions) {
		if (chromosomes == null || chromosomes.length == 0)
			return List.of();

		MapSqlParameterSource parameters = new MapSqlParameterSource()
				.addValue("chromosomes", chromosomes)
				.addValue("positions", gpositions);

		return jdbcTemplate.query(String.format(MAPPINGS_BY_CHR_POS, mappingTable), parameters, (rs, rowNum) -> createMapping(rs))
				.stream()
				.filter(gm -> Objects.nonNull(gm.getCodon()))
				.collect(Collectors.toList());
	}

    public List<GenomeToProteinMapping> getMappingsByAccPos(String[] accessions, Integer[] ppositions) {
        if (accessions == null || accessions.length == 0)
            return List.of();

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("accessions", accessions)
                .addValue("positions", ppositions);

        return jdbcTemplate.query(String.format(MAPPINGS_BY_ACC_POS, mappingTable), parameters, (rs, rowNum) ->
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

	private GenomeToProteinMapping createMapping(ResultSet rs) throws SQLException {
		return GenomeToProteinMapping.builder()
				.chromosome(rs.getString("chromosome"))
				.genomeLocation(rs.getInt("genomic_position"))
				.isoformPosition(rs.getInt("protein_position"))
				.baseNucleotide(rs.getString("allele"))
				.aa(rs.getString("protein_seq"))
				.codon(rs.getString("codon"))
				.accession(rs.getString("accession"))
				.ensg(ensWithVersion(rs.getString("ensg"), rs.getString("ensgv")))
				.ensp(ensWithVersion(rs.getString("ensp"), rs.getString("enspv")))
				.enst(ensWithVersion(rs.getString("enst"), rs.getString("enstv")))
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

    private String ensWithVersion(String id, String version) {
        if (id == null) return null;
        return version == null ? id : id + "." + version;
    }
}
