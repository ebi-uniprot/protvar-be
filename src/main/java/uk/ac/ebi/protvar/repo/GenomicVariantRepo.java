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
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.SearchTerm;
import uk.ac.ebi.protvar.types.SearchType;
import uk.ac.ebi.protvar.types.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class GenomicVariantRepo {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenomicVariantRepo.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.mapping}") private String mappingTable;
    @Value("${tbl.cadd}") private String caddTable;
    @Value("${tbl.allelefreq}") private String alleleFreqTable;
    @Value("${tbl.am}") private String amTable;
    @Value("${tbl.popeve}") private String popeveTable;
    @Value("${tbl.esm}") private String esmTable;
    @Value("${tbl.conserv}") private String conservationTable;
    @Value("${tbl.ann.str}") private String structureTable;
    @Value("${tbl.uprefseq}") private String uniprotRefseqTable;
    @Value("${tbl.pocket.v2}") private String pocketTable;
    @Value("${tbl.interaction}") private String interactionTable;
    @Value("${tbl.foldx}") private String foldxTable;
    @Value("${tbl.mapping.dbsnp}")
    private String dbsnpLookupTable;

    // ========================================================================
    // MAIN ENTRY POINT
    // ========================================================================

    public Page<VariantInput> get(MappingRequest request, Pageable pageable) {
        if (pageable == null) {
            LOGGER.warn("Defaulting to page {}, size {}.", 0+1, PageUtils.DEFAULT_PAGE_SIZE);
            pageable = PageRequest.of(0, PageUtils.DEFAULT_PAGE_SIZE);
        }
        boolean isDownload = request instanceof DownloadRequest; // no need to run countQuery for download

        // Determine filtering and sorting requirements
        boolean filterKnown = Boolean.TRUE.equals(request.getKnown());
        boolean filterByCadd = isFilteringRequired(request.getCadd());
        boolean filterByAm = isFilteringRequired(request.getAm());
        boolean filterByPopEve = isFilteringRequired(request.getPopeve());
        boolean filterByAlleleFreq = isFilteringRequired(request.getAlleleFreq());
        boolean filterByConservation = request.getConservationMin() != null || request.getConservationMax() != null;
        boolean filterByEsm1b = request.getEsm1bMin() != null || request.getEsm1bMax() != null;
        boolean filterExperimentalModel = Boolean.TRUE.equals(request.getExperimentalModel());
        boolean filterPocket = Boolean.TRUE.equals(request.getPocket());
        boolean filterInteract = Boolean.TRUE.equals(request.getInteract());
        boolean filterStability = request.getStability() != null && !request.getStability().isEmpty();

        boolean sortByCadd = "cadd".equalsIgnoreCase(request.getSort());
        boolean sortByAm = "am".equalsIgnoreCase(request.getSort());
        boolean sortByPopEve = "popeve".equalsIgnoreCase(request.getSort());
        boolean sortByEsm1b = "esm1b".equalsIgnoreCase(request.getSort());

        MapSqlParameterSource parameters = new MapSqlParameterSource();

        // Determine which joins are needed
        boolean joinCadd = filterByCadd || sortByCadd;
        boolean joinAm = filterByAm || sortByAm;
        boolean joinPopEve = filterByPopEve || sortByPopEve;
        boolean joinEsm1b = filterByEsm1b || sortByEsm1b;
        boolean joinCodonTable = joinAm || joinPopEve || joinEsm1b || filterStability;

        // Build query based on what we have
        StringBuilder query = new StringBuilder();

        boolean hasIdentifiers = request.getSearchTerms() != null && !request.getSearchTerms().isEmpty();

        if (hasIdentifiers) {
            // STRATEGY 1: Start with identifier filtering (best case)
            buildQueryWithIdentifiers(query, parameters,
                    filterPocket, filterInteract, filterExperimentalModel,
                    filterKnown, joinCodonTable,
                    joinCadd, joinAm, joinPopEve, joinEsm1b,
                    filterByAlleleFreq, filterByConservation, filterStability,
                    filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                    request);
        } else {

            // Safety check: At least one filter should be active
            boolean hasAnyFilter = filterKnown || filterByCadd || filterByAm || filterByPopEve
                    || filterByAlleleFreq || filterByConservation || filterByEsm1b
                    || filterExperimentalModel || filterPocket || filterInteract || filterStability;

            if (!hasAnyFilter) {
                throw new IllegalArgumentException(
                        "Database-wide queries without any filters are not supported. " +
                                "Please specify at least one filter or provide search terms."
                );
            }

            if (filterPocket || filterInteract || filterExperimentalModel) {
            // STRATEGY 2: Start with feature filters (moderately selective)
            buildQueryWithFeatureFilters(query, parameters,
                    filterPocket, filterInteract, filterExperimentalModel,
                    filterKnown, joinCodonTable,
                    joinCadd, joinAm, joinPopEve, joinEsm1b,
                    filterByAlleleFreq, filterByConservation, filterStability,
                    filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                    request);
            } else if (filterKnown) {
                // STRATEGY 3: Start with dbSNP filter (moderately selective)
                buildQueryWithDbsnpFilter(query,
                        joinCodonTable, joinCadd, joinAm, joinPopEve, joinEsm1b,
                        filterByAlleleFreq, filterByConservation, filterStability,
                        filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
            } else {
                // STRATEGY 4: No highly selective filters - start with most selective score filter
                // This is expensive but sometimes unavoidable
                buildQueryWithScoreFilters(query,
                        joinCodonTable, joinCadd, joinAm, joinPopEve, joinEsm1b,
                        filterByAlleleFreq, filterByConservation, filterStability,
                        filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
            }
        }

        // Add final WHERE clause filters
        query.append("WHERE 1=1\n");
        addFilters(query, parameters, request,
                filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                filterByConservation, filterByEsm1b, filterStability);

        // Build SELECT fields - we'll add score columns dynamically
        String baseSelect = "m.chromosome, m.genomic_position, m.allele, alleles.alt_allele, " +
                "m.protein_position, m.codon_position, m.accession";
        StringBuilder selectFields = new StringBuilder(baseSelect);

        // Add score fields if needed for sorting
        if (sortByCadd) selectFields.append(", cadd.score");
        else if (sortByAm) selectFields.append(", am.am_pathogenicity");
        else if (sortByPopEve) selectFields.append(", popeve.popeve");
        else if (sortByEsm1b) selectFields.append(", esm.score");

        // Count query
        long total = -1;
        if (!isDownload) {
            String countSql = "SELECT COUNT(*) FROM (\n" + query + "\n) counted"; // COUNT(DISTINCT(baseSelect))
            total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);

            if (total == 0) {
                return Page.empty(pageable);
            }
        }

        // Add ORDER BY
        String sortOrder = "asc".equalsIgnoreCase(request.getOrder()) ? "ASC" : "DESC";
        query.append("ORDER BY ");
        if (sortByCadd) {
            query.append("cadd.score ").append(sortOrder).append(", ");
        } else if (sortByAm) {
            query.append("am.am_pathogenicity ").append(sortOrder).append(", ");
        } else if (sortByPopEve) {
            query.append("popeve.popeve ").append(sortOrder).append(", ");
        } else if (sortByEsm1b) {
            query.append("esm.score ").append(sortOrder).append(", ");
        }
        query.append("m.chromosome, m.genomic_position, m.protein_position, alleles.alt_allele\n");

        // Add pagination
        query.append("LIMIT :pageSize OFFSET :offset\n");
        parameters.addValue("pageSize", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());

        // Execute query
        String finalQuery = "SELECT " + selectFields + " FROM (\n" + query + "\n) results"; // DISTINCT(baseSelect)
        List<VariantInput> variants = jdbcTemplate.query(finalQuery, parameters,
                (rs, rowNum) -> {
                    String chr = rs.getString("chromosome");
                    int pos = rs.getInt("genomic_position");
                    String ref = rs.getString("allele");
                    String alt = rs.getString("alt_allele");
                    return new GenomicInput(
                            "%s %d %s %s".formatted(chr, pos, ref, alt),
                            chr, pos, ref, alt
                    );
        });

        return isDownload ? new PageImpl<>(variants) : new PageImpl<>(variants, pageable, total);
    }

    /**
     * STRATEGY 1: Build query starting with identifier filtering (most selective)
     */
    private void buildQueryWithIdentifiers(
            StringBuilder query, MapSqlParameterSource parameters,
            boolean filterPocket, boolean filterInteract, boolean filterExperimentalModel,
            boolean filterKnown, boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        List<String> ctes = new ArrayList<>();
        List<String> unionBranches = new ArrayList<>();

        // Build identifier CTEs and UNION branches
        buildIdentifierCTEsAndBranches(ctes, unionBranches, parameters, request.getSearchTerms());

        // Start query with CTEs
        if (!ctes.isEmpty()) {
            query.append("WITH ").append(String.join(",\n", ctes)).append(",\n");
        } else {
            query.append("WITH ");
        }

        // Add filtered_mapping CTE
        query.append("filtered_mapping AS (\n");
        query.append(String.join("\n\nUNION\n\n", unionBranches));
        query.append("\n),\n");

        // PHASE 1: Apply highly selective feature filters BEFORE alt_allele expansion
        // This reduces the row count before multiplication
        boolean hasFeatureFilters = filterPocket || filterInteract || filterExperimentalModel;

        if (hasFeatureFilters) {
            query.append(",\nfeature_filtered AS (\n");
            query.append("  SELECT fm.*\n");
            query.append("  FROM filtered_mapping fm\n");

            // pocket_v2: 547K rows - highly specific positions
            if (filterPocket) {
                query.append("""
                        INNER JOIN (
                            SELECT DISTINCT struct_id, unnest(pocket_resid) as position
                            FROM %s
                        ) p ON p.struct_id = fm.accession AND p.position = fm.protein_position
                        """.formatted(pocketTable));
            }

            // af2complexes_interaction: 69K rows - highly specific
            if (filterInteract) {
                query.append("""
                        INNER JOIN (
                          SELECT accession, position FROM (
                              SELECT a as accession, unnest(a_residues) as position FROM %s
                              UNION ALL
                              SELECT b as accession, unnest(b_residues) as position FROM %s
                          ) i GROUP BY accession, position
                        ) interact ON interact.accession = fm.accession 
                          AND interact.position = fm.protein_position
                        """.formatted(interactionTable, interactionTable));
            }

            // rel_2025_01_structure: 203K rows - specific when filtering
            if (filterExperimentalModel) {
                query.append("""
                        INNER JOIN (
                          SELECT DISTINCT accession, unp_start, unp_end
                          FROM %s
                        ) struct ON struct.accession = fm.accession 
                          AND fm.protein_position BETWEEN struct.unp_start AND struct.unp_end
                        """.formatted(structureTable));
            }

            query.append("\n)\n");
        }

        // PHASE 2: Expand alt_alleles and calculate codon
        String sourceTable = hasFeatureFilters ? "feature_filtered" : "filtered_mapping";
        query.append("SELECT\n");
        query.append("  ff.chromosome, ff.genomic_position, ff.allele, ff.accession,\n");
        query.append("  ff.protein_position, ff.codon_position, ff.protein_seq,\n");
        query.append("  ff.codon, ff.reverse_strand,\n");
        query.append("  alleles.alt_allele");

        if (joinCodonTable) {
            query.append(",\n  c.amino_acid as mt_aa");
        }

        query.append("\nFROM ").append(sourceTable).append(" ff\n");
        query.append("CROSS JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alleles(alt_allele)\n");
        query.append("  ON alleles.alt_allele <> ff.allele\n");

        // PHASE 3: Calculate codon
        if (joinCodonTable) {
            query.append("""
                    LEFT JOIN codon_table c ON c.codon = UPPER(CASE
                      WHEN ff.codon_position = 1 THEN rna_base_for_strand(alleles.alt_allele, ff.reverse_strand) || substring(ff.codon, 2, 2)
                      WHEN ff.codon_position = 2 THEN substring(ff.codon, 1, 1) || rna_base_for_strand(alleles.alt_allele, ff.reverse_strand) || substring(ff.codon, 3, 1)
                      WHEN ff.codon_position = 3 THEN substring(ff.codon, 1, 2) || rna_base_for_strand(alleles.alt_allele, ff.reverse_strand)
                      ELSE ff.codon
                    END)
                    """);
        }

        // Wrap in another SELECT to rename columns back to 'm.*'
        String innerQuery = query.toString();
        query.setLength(0);
        query.append("SELECT * FROM (\n");
        query.append(innerQuery);
        query.append(") m\n");

        // PHASE 4: Apply remaining filters and joins
        addRemainingJoins(query, filterKnown, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
    }

    /**
     * STRATEGY 2: Build query starting with feature filters (no identifiers)
     */
    private void buildQueryWithFeatureFilters(
            StringBuilder query,
            MapSqlParameterSource parameters,
            boolean filterPocket, boolean filterInteract, boolean filterExperimentalModel,
            boolean filterKnown, boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        // Use semi-join approach: find positions from feature tables, then join with mapping
        query.append("WITH feature_positions AS (\n");

        List<String> featureUnions = new ArrayList<>();

        if (filterPocket) {
            featureUnions.add(String.format("""
          SELECT DISTINCT struct_id as accession, unnest(pocket_resid) as position
          FROM %s
        """, pocketTable));
        }

        if (filterInteract) {
            featureUnions.add(String.format("""
          SELECT accession, position FROM (
              SELECT a as accession, unnest(a_residues) as position FROM %s
              UNION ALL
              SELECT b as accession, unnest(b_residues) as position FROM %s
          ) i
        """, interactionTable, interactionTable));
        }

        if (filterExperimentalModel) {
            featureUnions.add(String.format("""
          SELECT s.accession, pos as position
          FROM %s s
          CROSS JOIN generate_series(s.unp_start, s.unp_end) as pos
        """, structureTable));
        }

        query.append(String.join("\n  UNION\n", featureUnions));
        query.append("\n)\n");

        // Now join with mapping table and expand alt_alleles
        query.append(String.format("""
      SELECT
        m.chromosome, m.genomic_position, m.allele, m.accession,
        m.protein_position, m.codon_position, m.protein_seq,
        m.codon, m.reverse_strand,
        alleles.alt_allele
      """));

        if (joinCodonTable) {
            query.append(",\n  c.amino_acid as mt_aa");
        }

        query.append(String.format("""
      
      FROM %s m
      INNER JOIN feature_positions fp 
          ON fp.accession = m.accession 
          AND fp.position = m.protein_position
      CROSS JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alleles(alt_allele)
          ON alleles.alt_allele <> m.allele
    """, mappingTable));

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(alleles.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(alleles.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(alleles.alt_allele, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        // Wrap and add remaining joins
        String innerQuery = query.toString();
        query.setLength(0);
        query.append("SELECT * FROM (\n");
        query.append(innerQuery);
        query.append("\n) m\n");

        addRemainingJoins(query, filterKnown, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
    }

    /**
     * STRATEGY 3: Build query starting with dbSNP filter
     */
    private void buildQueryWithDbsnpFilter(
            StringBuilder query,
            boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b) {

        // Start with dbSNP table (known variants)
        query.append(String.format("""
      SELECT
        m.chromosome, m.genomic_position, m.allele, m.accession,
        m.protein_position, m.codon_position, m.protein_seq,
        m.codon, m.reverse_strand,
        alt.alt_allele
      """));

        if (joinCodonTable) {
            query.append(",\n  c.amino_acid as mt_aa");
        }

        query.append(String.format("""
      
      FROM %s d
      CROSS JOIN LATERAL unnest(d.known_alts) AS alt(alt_allele)
      INNER JOIN %s m ON m.chromosome = d.chr
          AND m.genomic_position = d.pos
          AND m.allele = d.ref
    """, dbsnpLookupTable, mappingTable));

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(alt.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(alt.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(alt.alt_allele, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        // Wrap and add remaining joins
        String innerQuery = query.toString();
        query.setLength(0);
        query.append("SELECT * FROM (\n");
        query.append(innerQuery);
        query.append("\n) m\n");

        addRemainingJoins(query, false, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
    }

    /**
     * STRATEGY 4: Build query with score filters only (least optimal - full table scan risk)
     */
    private void buildQueryWithScoreFilters(
            StringBuilder query,
            boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b) {

        // No highly selective filters - must scan mapping table
        // At least expand alt_alleles and compute codon first before joining huge score tables
        query.append(String.format("""
      SELECT
        m.chromosome, m.genomic_position, m.allele, m.accession,
        m.protein_position, m.codon_position, m.protein_seq,
        m.codon, m.reverse_strand,
        alleles.alt_allele
      """));

        if (joinCodonTable) {
            query.append(",\n  c.amino_acid as mt_aa");
        }

        query.append(String.format("""
      
      FROM %s m
      CROSS JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alleles(alt_allele)
          ON alleles.alt_allele <> m.allele
    """, mappingTable));

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(alleles.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(alleles.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(alleles.alt_allele, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        // Wrap and add remaining joins
        String innerQuery = query.toString();
        query.setLength(0);
        query.append("SELECT * FROM (\n");
        query.append(innerQuery);
        query.append("\n) m\n");

        addRemainingJoins(query, false, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
    }

    /*
    Critical indexes:
    -- Essential for refseq queries (accession-only lookup)
    CREATE INDEX idx_mapping_accession ON rel_2025_01_genomic_protein_mapping (accession);

    -- Essential for pdb queries (accession + position lookup)
    CREATE INDEX idx_mapping_acc_pos ON rel_2025_01_genomic_protein_mapping (accession, start_pos, end_pos);

    CREATE INDEX idx_mapping_gene_name ON rel_2025_01_genomic_protein_mapping (gene_name);

    CREATE INDEX idx_mapping_ensg ON rel_2025_01_genomic_protein_mapping (ensg);
    CREATE INDEX idx_mapping_enst ON rel_2025_01_genomic_protein_mapping (enst);
    CREATE INDEX idx_mapping_ensp ON rel_2025_01_genomic_protein_mapping (ensp);

    -- Composite indexes for version matching
    CREATE INDEX idx_mapping_ensg_ver ON rel_2025_01_genomic_protein_mapping (ensg, ensgv);
    CREATE INDEX idx_mapping_enst_ver ON rel_2025_01_genomic_protein_mapping (enst, enstv);
    CREATE INDEX idx_mapping_ensp_ver ON rel_2025_01_genomic_protein_mapping (ensp, enspv);


    -- For the CASE condition (LIKE and = operations)
    CREATE INDEX idx_refseq_acc_pattern ON rel_2025_01_uniprot_refseq (refseq_acc varchar_pattern_ops);
    -- For the JOIN output (optional but recommended)
    CREATE INDEX idx_uniprot_acc ON rel_2025_01_uniprot_refseq (uniprot_acc);

    -- For PDB lookup
    CREATE INDEX idx_pdb_mapping_id ON rel_2025_01_pdb_mapping (pdb_id);
     */
    private void buildIdentifierCTEsAndBranches(List<String> ctes, List<String> unionBranches,
                                                MapSqlParameterSource parameters,
                                                List<SearchTerm> searchTerms) {
        // Parse identifiers by type
        Map<SearchType, List<String>> identifiersByType = searchTerms.stream()
                .collect(Collectors.groupingBy(
                        SearchTerm::getType,
                        Collectors.mapping(SearchTerm::getValue, Collectors.toList())
                ));

        List<String> refseqIds = identifiersByType.get(SearchType.REFSEQ);
        List<String> pdbIds = identifiersByType.get(SearchType.PDB);
        List<String> uniprotIds = identifiersByType.get(SearchType.UNIPROT);
        List<String> geneIds = identifiersByType.get(SearchType.GENE);
        List<String> ensemblIds = identifiersByType.get(SearchType.ENSEMBL);

        // Add RefSeq CTE if needed
        if (refseqIds != null && !refseqIds.isEmpty()) {
            if (refseqIds.size() == 1) {
                // Single input - simple WHERE clause
                String refseqAcc = refseqIds.get(0);
                ctes.add("""
                    refseq_acc AS (
                        SELECT DISTINCT r.uniprot_acc
                        FROM %s r
                        WHERE r.refseq_acc %s
                    """.formatted(
                    uniprotRefseqTable,
                    refseqAcc.contains(".") ? "= :refseqAcc" : "LIKE :refseqAcc || '.%%'"));
                parameters.addValue("refseqAcc", refseqAcc);
            } else {
                // Multiple inputs - VALUES with JOIN
                ctes.add("""
                    refseq_acc AS (
                        SELECT DISTINCT r.uniprot_acc
                        FROM %s r
                        JOIN unnest(:refseqAccs) AS input(acc) ON (
                            CASE
                                WHEN input.acc LIKE '%%.%%' THEN r.refseq_acc = input.acc
                                ELSE r.refseq_acc LIKE input.acc || '.%%'
                            END
                        )
                    )
                    """.formatted(uniprotRefseqTable));
                parameters.addValue("refseqAccs", refseqIds.toArray(new String[0]));
            }

            unionBranches.add("""
                SELECT m.*
                FROM %s m
                JOIN refseq_acc ra ON m.accession = ra.uniprot_acc
                """.formatted(mappingTable));
        }

        // Add PDB CTE if needed
        if (pdbIds != null && !pdbIds.isEmpty()) {
            ctes.add("""
                pdb_acc AS (
                    SELECT DISTINCT p.accession, p.unp_start, p.unp_end
                    FROM %s p
                    WHERE p.pdb_id = ANY(:pdbIds)
                )
                """.formatted(structureTable));
            parameters.addValue("pdbIds", pdbIds.toArray(new String[0]));
            unionBranches.add("""
                SELECT m.*
                FROM %s m
                JOIN pdb_acc pa ON m.accession = pa.accession 
                    AND m.protein_position BETWEEN pa.unp_start AND pa.unp_end
                """.formatted(mappingTable));
        }

        if (uniprotIds != null && !uniprotIds.isEmpty()) {
            unionBranches.add("""
                SELECT m.*
                FROM %s m
                WHERE m.accession = ANY(:uniprotAccs)
                """.formatted(mappingTable));
            parameters.addValue("uniprotAccs", uniprotIds.toArray(new String[0]));
        }

        if (geneIds != null && !geneIds.isEmpty()) {
            unionBranches.add("""
                SELECT m.*
                FROM %s m
                WHERE m.gene_name = ANY(:geneIds)
                """.formatted(mappingTable));
            parameters.addValue("geneIds", geneIds.toArray(new String[0]));
        }

        if (ensemblIds != null && !ensemblIds.isEmpty()) {

            List<String> conditions = buildEnsemblConditions(ensemblIds, parameters);
            if (!conditions.isEmpty()) {
                unionBranches.add("""
                    SELECT m.*
                    FROM %s m
                    WHERE %s
                    """.formatted(mappingTable, String.join(" OR ", conditions)));
            }
        }
    }

    private List<String> buildEnsemblConditions(
            List<String> ensemblIds,
            MapSqlParameterSource parameters) {

        List<String> conditions = new ArrayList<>();

        for (int i = 0; i < ensemblIds.size(); i++) {
            String id = ensemblIds.get(i);
            Matcher matcher = Pattern.compile("^(ENS[GTPE])(\\d{11})(\\.\\d+)?$",
                    Pattern.CASE_INSENSITIVE).matcher(id);

            if (!matcher.matches()) continue;

            String prefix = matcher.group(1).toUpperCase();
            String baseId = prefix + matcher.group(2);
            String versionStr = matcher.group(3);
            String version = versionStr != null ? versionStr.substring(1) : null;

            String idColumn = switch (prefix) {
                case "ENSG" -> "ensg";
                case "ENST" -> "enst";
                case "ENSP" -> "ensp";
                // ENSE?
                default -> null;
            };

            if (idColumn == null) continue;

            String idParam = "ensId_" + i;
            parameters.addValue(idParam, baseId);

            String condition = "m." + idColumn + " = :" + idParam;

            if (version != null) {
                String versionParam = "ensVer_" + i;
                parameters.addValue(versionParam, version);
                condition += " AND m." + idColumn + "v = :" + versionParam;
            }

            conditions.add("(" + condition + ")");
        }

        return conditions;
    }


    /**
     * Add remaining joins in optimal order (after base filtering)
     */
    private void addRemainingJoins(
            StringBuilder query,
            boolean filterKnown,
            boolean filterByAlleleFreq, boolean filterByConservation,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByEsm1b) {

        // dbSNP filter (if not already applied in Strategy 3)
        if (filterKnown) {
            query.append("""
                    INNER JOIN %s d ON d.chr = m.chromosome
                      AND d.pos = m.genomic_position
                      AND d.ref = m.allele
                      AND m.alt_allele = ANY(d.known_alts)
                    """.formatted(dbsnpLookupTable));
        }

        // Conservation (14M rows - moderately selective)
        if (filterByConservation) {
            query.append("""
                    INNER JOIN %s cons ON cons.accession = m.accession 
                      AND cons.position = m.protein_position 
                      AND cons.aa = m.protein_seq
                    """.formatted(conservationTable));
        }

        // Allele frequency (52M rows - moderately selective)
        if (filterByAlleleFreq) {
            query.append("""
                    INNER JOIN %s af ON af.chr = m.chromosome 
                      AND af.pos = m.genomic_position 
                      AND af.ref = m.allele 
                      AND af.alt = m.alt_allele
                    """.formatted(alleleFreqTable));
        }

        // Large score tables - join LAST (500M, 216M, etc.)
        addScoreTableJoins(query,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);
    }

    /**
     * Add large score table joins (always join these LAST)
     */
    private void addScoreTableJoins(
            StringBuilder query,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByEsm1b) {
        // Use LEFT JOIN if only for sorting to avoid unnecessary filtering

        // CADD: 500M rows
        if (joinCadd) {
            String joinType = filterByCadd ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s cadd ON cadd.chromosome = m.chromosome 
                  AND cadd.position = m.genomic_position 
                  AND cadd.reference_allele = m.allele 
                  AND cadd.alt_allele = m.alt_allele
                """.formatted(joinType, caddTable));
        }

        // AlphaMissense: 216M rows
        if (joinAm) {
            String joinType = filterByAm ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s am ON am.accession = m.accession 
                  AND am.position = m.protein_position 
                  AND am.wt_aa = m.protein_seq 
                  AND am.mt_aa = m.mt_aa
                """.formatted(joinType, amTable));
        }

        // PopEVE: 185M rows (also needs RefSeq lookup)
        if (joinPopEve) {
            String joinType = filterByPopEve ? "INNER" : "LEFT";
            // RefSeq: 113K rows
            query.append("""
                %s JOIN %s unp_ref ON unp_ref.uniprot_acc = m.accession
                %s JOIN %s popeve ON popeve.refseq_protein = unp_ref.refseq_acc 
                  AND popeve.position = m.protein_position 
                  AND popeve.wt_aa = m.protein_seq 
                  AND popeve.mt_aa = m.mt_aa
                """.formatted(joinType, uniprotRefseqTable, joinType, popeveTable));
        }

        // ESM1b: 216M rows
        if (joinEsm1b) {
            String joinType = filterByEsm1b ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s esm ON esm.accession = m.accession 
                  AND esm.position = m.protein_position 
                  AND esm.mt_aa = m.mt_aa
                """.formatted(joinType, esmTable));
        }

        // FoldX: 209M rows
        if (filterStability) {
            query.append("""
                INNER JOIN %s f ON f.protein_acc = m.accession 
                  AND f.position = m.protein_position 
                  AND f.mutated_type = m.mt_aa
                """.formatted(foldxTable));
        }
    }

    /**
     * Adds WHERE clause filters based on request parameters.
     */
    private void addFilters(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            MappingRequest request,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByAlleleFreq, boolean filterByConservation,
            boolean filterByEsm1b, boolean filterStability) {

        // CADD filter
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
            sql.append("AND (").append(String.join(" OR ", caddClauses)).append(")\n");
        }

        // AlphaMissense filter
        if (filterByAm) {
            Integer[] amValues = request.getAm().stream()
                    .map(AmClass::getValue)
                    .toArray(Integer[]::new);
            sql.append("AND am.am_class = ANY(:amClasses)\n");
            parameters.addValue("amClasses", amValues);
        }

        // PopEVE filter
        if (filterByPopEve) {
            List<String> popeveClauses = new ArrayList<>();
            for (int i = 0; i < request.getPopeve().size(); i++) {
                PopEveClass category = request.getPopeve().get(i);
                String minParam = "popeveMin" + i;
                String maxParam = "popeveMax" + i;

                // Handle infinity values
                if (Double.isInfinite(category.getMin()) && category.getMin() < 0) {
                    popeveClauses.add("(popeve.popeve < :" + maxParam + ")");
                    parameters.addValue(maxParam, category.getMax());
                } else if (Double.isInfinite(category.getMax())) {
                    popeveClauses.add("(popeve.popeve >= :" + minParam + ")");
                    parameters.addValue(minParam, category.getMin());
                } else {
                    popeveClauses.add("(popeve.popeve >= :" + minParam + " AND popeve.popeve < :" + maxParam + ")");
                    parameters.addValue(minParam, category.getMin());
                    parameters.addValue(maxParam, category.getMax());
                }
            }
            sql.append("AND (").append(String.join(" OR ", popeveClauses)).append(")\n");
        }

        // Allele frequency filter
        if (filterByAlleleFreq) {
            List<String> afClauses = new ArrayList<>();
            for (int i = 0; i < request.getAlleleFreq().size(); i++) {
                AlleleFreqCategory category = request.getAlleleFreq().get(i);
                String minParam = "afMin" + i;
                String maxParam = "afMax" + i;
                afClauses.add("(af.af >= :" + minParam + " AND af.af < :" + maxParam + ")");
                parameters.addValue(minParam, category.getMin());
                parameters.addValue(maxParam, category.getMax());
            }
            sql.append("AND (").append(String.join(" OR ", afClauses)).append(")\n");
        }

        // Conservation filter
        if (filterByConservation) {
            if (request.getConservationMin() != null) {
                sql.append("AND cons.score >= :conservationMin\n");
                parameters.addValue("conservationMin", request.getConservationMin());
            }
            if (request.getConservationMax() != null) {
                sql.append("AND cons.score <= :conservationMax\n");
                parameters.addValue("conservationMax", request.getConservationMax());
            }
        }

        // ESM1b filter
        if (filterByEsm1b) {
            if (request.getEsm1bMin() != null) {
                sql.append("AND esm.score >= :esm1bMin\n");
                parameters.addValue("esm1bMin", request.getEsm1bMin());
            }
            if (request.getEsm1bMax() != null) {
                sql.append("AND esm.score <= :esm1bMax\n");
                parameters.addValue("esm1bMax", request.getEsm1bMax());
            }
        }

        // Stability filter
        if (filterStability) {
            Set<StabilityChange> changes = EnumSet.copyOf(request.getStability());
            if (!changes.equals(EnumSet.allOf(StabilityChange.class))) {
                if (changes.contains(StabilityChange.LIKELY_DESTABILISING)) {
                    sql.append("AND f.foldx_ddg >= 2\n");
                } else if (changes.contains(StabilityChange.UNLIKELY_DESTABILISING)) {
                    sql.append("AND f.foldx_ddg < 2\n");
                }
            }
        }
    }

    private <T> boolean isFilteringRequired(List<T> categories) {
        return categories != null && !categories.isEmpty();
    }
}
