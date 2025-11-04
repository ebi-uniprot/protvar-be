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
import uk.ac.ebi.protvar.utils.SearchTypeResolver;

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

        if (request.hasNoSearchTerms()) { // filter-only
            // Use filter-first strategy for database-wide queries
            return getGenomicVariantsForFilterOnlyQuery(request, pageable, isDownload,
                    filterKnown, filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                    filterByConservation, filterByEsm1b, filterExperimentalModel, filterPocket,
                    filterInteract, filterStability, sortByCadd, sortByAm, sortByPopEve, sortByEsm1b);
        }

        // Original path for identifier-based queries (this is fast)
        return getGenomicVariantsForIdentifierQuery(request, pageable, isDownload,
                filterKnown, filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                filterByConservation, filterByEsm1b, filterExperimentalModel, filterPocket,
                filterInteract, filterStability, sortByCadd, sortByAm, sortByPopEve, sortByEsm1b);
    }

    /**
     * OPTIMIZED: Filter-first strategy for queries without search terms.
     * Instead of expanding all variants then filtering, we:
     * 1. Filter each dataset independently
     * 2. Join filtered subsets
     * 3. Generate only matching variants
     * Filter-only: Need to filter datasets FIRST, then intersect
     * Pattern: Filter each dataset → Find intersection → Generate only those variants
     */
    private Page<VariantInput> getGenomicVariantsForFilterOnlyQuery(
            MappingRequest request, Pageable pageable, boolean isDownload,
            boolean filterKnown, boolean filterByCadd, boolean filterByAm,
            boolean filterByPopEve, boolean filterByAlleleFreq,
            boolean filterByConservation, boolean filterByEsm1b,
            boolean filterExperimentalModel, boolean filterPocket,
            boolean filterInteract, boolean filterStability,
            boolean sortByCadd, boolean sortByAm, boolean sortByPopEve, boolean sortByEsm1b) {

        MapSqlParameterSource parameters = new MapSqlParameterSource();

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

        String sortOrder = "asc".equalsIgnoreCase(request.getOrder()) ? "asc" : "desc";
        String fields = "m.chromosome, m.genomic_position, m.allele, m.alt_allele, m.protein_position, m.codon_position";

        StringBuilder sql = new StringBuilder();

        // Build CTEs for each filtered dataset
        boolean needsCodon = filterByAm || filterByPopEve || filterByEsm1b || filterStability;

        // CTE 1: Filtered allele frequencies (if applicable)
        if (filterByAlleleFreq) {
            sql.append(String.format("""
                WITH filtered_allele_freq AS (
                    SELECT chr, pos, ref, alt
                    FROM %s
                    WHERE 
            """, alleleFreqTable));

            List<String> afClauses = new ArrayList<>();
            for (int i = 0; i < request.getAlleleFreq().size(); i++) {
                AlleleFreqCategory category = request.getAlleleFreq().get(i);
                String minParam = "afMin" + i;
                String maxParam = "afMax" + i;
                afClauses.add("(af >= :" + minParam + " AND af < :" + maxParam + ")");
                parameters.addValue(minParam, category.getMin());
                parameters.addValue(maxParam, category.getMax());
            }
            sql.append(String.join(" OR ", afClauses))
                    .append("LIMIT 5000000), "); // Safety limit - adjust if needed
        }

        // CTE 2: Filtered PopEVE scores (if applicable)
        if (filterByPopEve) {
            sql.append(String.format("""
                filtered_popeve AS (
                    SELECT unp_ref.uniprot_acc, p.position, p.wt_aa, p.mt_aa, p.popeve
                    FROM %s p
                    INNER JOIN %s unp_ref ON unp_ref.refseq_acc = p.refseq_protein
                    WHERE 
                    """, popeveTable, uniprotRefseqTable));

            List<String> popeveClauses = new ArrayList<>();
            for (int i = 0; i < request.getPopeve().size(); i++) {
                PopEveClass category = request.getPopeve().get(i);
                String minParam = "popeveMin" + i;
                String maxParam = "popeveMax" + i;

                if (Double.isInfinite(category.getMin()) && category.getMin() < 0) {
                    popeveClauses.add("(p.popeve < :" + maxParam + ")");
                    parameters.addValue(maxParam, category.getMax());
                } else if (Double.isInfinite(category.getMax())) {
                    popeveClauses.add("(p.popeve >= :" + minParam + ")");
                    parameters.addValue(minParam, category.getMin());
                } else {
                    popeveClauses.add("(p.popeve >= :" + minParam + " AND p.popeve < :" + maxParam + ")");
                    parameters.addValue(minParam, category.getMin());
                    parameters.addValue(maxParam, category.getMax());
                }
            }
            sql.append(String.join(" OR ", popeveClauses))
                    .append("LIMIT 5000000), "); // Safety limit
        }

        // CTE 3: Main mapping with filtered joins
        String altAllele = filterByAlleleFreq ? "af.alt as alt_allele" : "alt.alt_allele";
        sql.append(String.format("""
                mapping_with_variants AS (
                  SELECT DISTINCT\n");
                    m.chromosome, m.genomic_position, m.allele, m.accession,
                    m.protein_position, m.codon_position, m.protein_seq,
                    m.codon, m.reverse_strand, %s
                  FROM %s m
                    """, altAllele, mappingTable));

        // Strategy: Join with FILTERED datasets first, then expand variants
        if (filterByAlleleFreq) {
            // This is the key optimization: only expand variants that exist in filtered allele freq
            sql.append("""
                INNER JOIN filtered_allele_freq af ON
                    af.chr = m.chromosome AND
                    af.pos = m.genomic_position AND
                    af.ref = m.allele AND
                    af.alt <> m.allele
                    """);
        } else {
            // No allele freq filter, so we need to expand all variants
            sql.append("""
                CROSS JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS alt(alt_allele)
                WHERE alt.alt_allele <> m.allele
                """);
        }

        // Early filter with PopEVE if available
        if (filterByPopEve) {
            sql.append("""
                INNER JOIN filtered_popeve fp ON
                    fp.uniprot_acc = m.accession AND
                    fp.position = m.protein_position AND
                    fp.wt_aa = m.protein_seq
                """);
        }

        // Add other structure/interaction filters early
        if (filterExperimentalModel) {
            sql.append(String.format("""
                INNER JOIN %s struct ON
                    struct.accession = m.accession AND
                    m.protein_position >= struct.unp_start AND
                    m.protein_position <= struct.unp_end
                    """, structureTable));
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

        // Add dbsnp filter if known variants requested
        if (filterKnown) {
            String altAlleleCol = filterByAlleleFreq ?  "af.alt" : "alt.alt_allele";
            sql.append(String.format("""
                INNER JOIN %s d ON
                    d.chr = m.chromosome AND
                    d.pos = m.genomic_position AND
                    d.ref = m.allele AND
                    %s = ANY(d.known_alts)
                    """, dbsnpLookupTable, altAlleleCol));
        }

        sql.append(")\n"); // Close CTE

        // Main SELECT from the filtered CTE
        sql.append("""
            SELECT %s
            FROM mapping_with_variants m
        """);

        // Add remaining joins for scoring/filtering
        boolean joinCodonTable = needsCodon;
        if (joinCodonTable) {
            sql.append("""
            LEFT JOIN codon_table c ON c.codon = upper(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(m.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(m.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(m.alt_allele, m.reverse_strand)
              ELSE m.codon
            END)
            """);
        }

        // Join with AlphaMissense if needed
        if (filterByAm) {
            sql.append(String.format("""
            INNER JOIN %s am ON
              am.accession = m.accession AND
              am.position = m.protein_position AND
              am.wt_aa = m.protein_seq AND
              am.mt_aa = c.amino_acid
            """, amTable));
        }

        // Join with ESM1b if needed
        if (filterByEsm1b) {
            sql.append(String.format("""
            INNER JOIN %s esm ON
              esm.accession = m.accession AND
              esm.position = m.protein_position AND
              esm.mt_aa = c.amino_acid
            """, esmTable));
        }

        // Join with FoldX if needed
        if (filterStability) {
            sql.append(String.format("""
            INNER JOIN %s f ON
              f.protein_acc = m.accession AND
              f.position = m.protein_position AND
              f.mutated_type = c.amino_acid
            """, foldxTable));
        }

        // Join with conservation if needed
        if (filterByConservation) {
            sql.append(String.format("""
            INNER JOIN %s cons ON
              cons.accession = m.accession AND
              cons.position = m.protein_position AND
              cons.aa = m.protein_seq
            """, conservationTable));
        }

        // Add WHERE clause for remaining filters
        sql.append("WHERE 1=1\n");

        // PopEVE amino acid match (if not already filtered in CTE)
        if (filterByPopEve && joinCodonTable) {
            sql.append("  AND c.amino_acid IN (SELECT mt_aa FROM filtered_popeve WHERE uniprot_acc = m.accession AND position = m.protein_position)");
        }

        // AlphaMissense class filter
        if (filterByAm) {
            List<Integer> amValues = request.getAm().stream().map(AmClass::getValue).toList();
            sql.append("  AND am.am_class IN (:amClasses)");
            parameters.addValue("amClasses", amValues);
        }

        // Conservation filter
        if (filterByConservation) {
            if (request.getConservationMin() != null) {
                sql.append("  AND cons.score >= :conservationMin");
                parameters.addValue("conservationMin", request.getConservationMin());
            }
            if (request.getConservationMax() != null) {
                sql.append("  AND cons.score <= :conservationMax");
                parameters.addValue("conservationMax", request.getConservationMax());
            }
        }

        // ESM1b filter
        if (filterByEsm1b) {
            if (request.getEsm1bMin() != null) {
                sql.append("  AND esm.score >= :esm1bMin");
                parameters.addValue("esm1bMin", request.getEsm1bMin());
            }
            if (request.getEsm1bMax() != null) {
                sql.append("  AND esm.score <= :esm1bMax");
                parameters.addValue("esm1bMax", request.getEsm1bMax());
            }
        }

        // Stability filter
        if (filterStability) {
            Set<StabilityChange> changes = EnumSet.copyOf(request.getStability());
            if (!changes.equals(EnumSet.allOf(StabilityChange.class))) {
                if (changes.contains(StabilityChange.LIKELY_DESTABILISING)) {
                    sql.append("  AND f.foldx_ddg >= 2");
                } else if (changes.contains(StabilityChange.UNLIKELY_DESTABILISING)) {
                    sql.append("  AND f.foldx_ddg < 2");
                }
            }
        }

        // Count query
        long total = -1;
        if (!isDownload) {
            String countSql = String.format(sql.toString(), "COUNT(DISTINCT (" + fields + "))");
            LOGGER.info("Executing count query for filter-only search");
            total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);

            if (total == 0) {
                return Page.empty(pageable);
            }

            // Warn if result set is very large
            if (total > 100000) {
                LOGGER.warn("Filter-only query returned {} results. Consider adding more specific filters.", total);
            }
        }

        // Sorting
        sql.append("ORDER BY ");
        if (sortByPopEve && filterByPopEve) {
            fields += ", (SELECT popeve FROM filtered_popeve WHERE uniprot_acc = m.accession AND position = m.protein_position LIMIT 1) as popeve_score";
            sql.append("popeve_score ").append(sortOrder).append(", ");
        } else if (sortByAm && filterByAm) {
            fields += ", am.am_pathogenicity";
            sql.append("am.am_pathogenicity ").append(sortOrder).append(", ");
        } else if (sortByEsm1b && filterByEsm1b) {
            fields += ", esm.score";
            sql.append("esm.score ").append(sortOrder).append(", ");
        }
        sql.append("m.chromosome, m.genomic_position, m.protein_position, m.codon_position, m.alt_allele\n");

        // Pagination
        sql.append("LIMIT :pageSize OFFSET :offset");
        parameters.addValue("pageSize", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());

        // Execute query
        String finalSql = String.format(sql.toString(), "DISTINCT " + fields);
        LOGGER.info("Executing filter-only query");
        LOGGER.debug("SQL: {}", finalSql);

        List<VariantInput> variants = jdbcTemplate.query(
                finalSql,
                parameters,
                (rs, rowNum) -> {
                    String chr = rs.getString("chromosome");
                    int pos = rs.getInt("genomic_position");
                    String ref = rs.getString("allele");
                    String alt = rs.getString("alt_allele");
                    return new GenomicInput(
                            String.format("%s %d %s %s", chr, pos, ref, alt),
                            chr, pos, ref, alt
                    );
                }
        );

        return isDownload ?
                new PageImpl<>(variants) :
                new PageImpl<>(variants, pageable, total);
    }

    /**
     * ORIGINAL PATH: Identifier-based
     * Fast because we filter by identifier FIRST
     * Pattern: Filter to ~1000 rows → Expand to ~3000 variants → Apply other filters
     */
    private Page<VariantInput> getGenomicVariantsForIdentifierQuery(
            MappingRequest request, Pageable pageable, boolean isDownload,
            boolean filterKnown, boolean filterByCadd, boolean filterByAm,
            boolean filterByPopEve, boolean filterByAlleleFreq,
            boolean filterByConservation, boolean filterByEsm1b,
            boolean filterExperimentalModel, boolean filterPocket,
            boolean filterInteract, boolean filterStability,
            boolean sortByCadd, boolean sortByAm, boolean sortByPopEve, boolean sortByEsm1b) {

        // Determine which joins are needed
        boolean joinCadd = filterByCadd || sortByCadd;
        boolean joinAm = filterByAm || sortByAm;
        boolean joinPopEve = filterByPopEve || sortByPopEve;
        boolean joinEsm1b = filterByEsm1b || sortByEsm1b;
        boolean joinAlleleFreq = filterByAlleleFreq;
        boolean joinConservation = filterByConservation;
        boolean joinCodonTable = joinAm || joinPopEve || joinEsm1b || filterStability;

        String sortOrder = "asc".equalsIgnoreCase(request.getOrder()) ? "asc" : "desc";
        String fields = "m.chromosome, m.genomic_position, m.allele, m.alt_allele, m.protein_position, m.codon_position";

        // Build the base query
        String dbsnpJoin = buildDbsnpJoin(filterKnown);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String identifierConditions = buildIdentifierConditions(request.getSearchTerms(), parameters);

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
                .replaceFirst("%s", identifierConditions));


        // Add conditional joins in optimal order
        addConditionalJoins(sql,
                joinCadd, joinAm, joinPopEve, joinEsm1b, joinAlleleFreq,
                joinConservation, joinCodonTable, filterExperimentalModel,
                filterPocket, filterInteract, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b);


        sql.append(" WHERE 1=1");

        // Add filters
        addFilters(sql, parameters, request,
                filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                filterByConservation, filterByEsm1b, filterStability);

        // Count query
        long total = -1;
        if (!isDownload) {
            String countSql = String.format(sql.toString(), "COUNT(DISTINCT (" + fields + "))");
            total = jdbcTemplate.queryForObject(countSql, parameters, Long.class);

            // Early return if no results
            if (total == 0) {
                return Page.empty(pageable);
            }
        }

        // Sorting and pagination
        sql.append(" ORDER BY ");
        if (sortByCadd) {
            fields += ", cadd.score ";
            sql.append("cadd.score ").append(sortOrder).append(", ");
        } else if (sortByAm) {
            fields += ", am.am_pathogenicity ";
            sql.append("am.am_pathogenicity ").append(sortOrder).append(", ");
        } else if (sortByPopEve) {
            fields += ", popeve.popeve ";
            sql.append("popeve.popeve ").append(sortOrder).append(", ");
        } else if (sortByEsm1b) {
            fields += ", esm.score ";
            sql.append("esm.score ").append(sortOrder).append(", ");
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
                    return new GenomicInput(
                            String.format("%s %d %s %s", chr, pos, ref, alt),
                            chr, pos, ref, alt
                    );
                }
        );
        return isDownload ?
                new PageImpl<>(variants) : // unpaged, total count not needed
                new PageImpl<>(variants, pageable, total);
    }

    private <T> boolean isFilteringRequired(List<T> categories) {
        return categories != null
                && !categories.isEmpty();
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
                AND alt.alt_allele = ANY(d.known_alts))
        """, dbsnpLookupTable);
        }
        return "";
    }

    /**
     * Builds the WHERE/JOIN conditions for multiple identifiers.
     * Combines identifiers with OR within the same type, AND across types.
     * Returns empty condition if no identifiers provided (database-wide query).
     */
    private String buildIdentifierConditions(
            List<SearchTerm> searchTerms,
            MapSqlParameterSource parameters) {

        if (searchTerms == null || searchTerms.isEmpty()) {
            // No identifiers - return all variants (filter-only query)
            return "WHERE alt.alt_allele <> m.allele";
        }

        // Group identifiers by type
        Map<SearchType, List<String>> identifiersByType = searchTerms.stream()
                .collect(Collectors.groupingBy(
                        SearchTerm::getType,
                        Collectors.mapping(SearchTerm::getValue, Collectors.toList())
                ));

        // Build conditions for each type
        List<String> typeConditions = new ArrayList<>();
        StringBuilder joins = new StringBuilder();
        int paramCounter = 0;

        for (Map.Entry<SearchType, List<String>> entry : identifiersByType.entrySet()) {
            SearchType type = entry.getKey();
            List<String> values = entry.getValue();

            if (values.size() == 1) {
                // Optimized single-value condition
                IdentifierCondition condition = buildSingleIdentifierCondition(
                        values.get(0), type, parameters, paramCounter
                );
                if (condition.join != null) {
                    joins.append(condition.join);
                }
                typeConditions.add(condition.whereClause);
                paramCounter++;
            } else {
                // Multiple values of same type - use OR
                IdentifierCondition condition = buildMultiIdentifierCondition(
                        values, type, parameters, paramCounter
                );
                if (condition.join != null) {
                    joins.append(condition.join);
                }
                typeConditions.add(condition.whereClause);
                paramCounter += values.size();
            }
        }

        // Combine all conditions with AND, add common filter
        String whereClause = typeConditions.isEmpty()
                ? "WHERE alt.alt_allele <> m.allele"
                : "WHERE (" + String.join(" OR ", typeConditions) + ")  AND alt.alt_allele <> m.allele";

        return joins + "\n" + whereClause;
    }

    /**
     * Builds condition for a single identifier value.
     */
    private IdentifierCondition buildSingleIdentifierCondition(
            String value,
            SearchType type,
            MapSqlParameterSource parameters,
            int paramIndex) {

        String paramName = "id" + paramIndex;

        return switch (type) {
            case UNIPROT -> {
                parameters.addValue(paramName, value);
                yield new IdentifierCondition(null, "m.accession = :" + paramName);
            }

            case GENE -> {
                parameters.addValue(paramName, value);
                yield new IdentifierCondition(null, "m.gene_name = :" + paramName);
            }

            case ENSEMBL -> buildEnsemblCondition(value, parameters, paramIndex);

            case PDB -> {
                parameters.addValue(paramName, value);
                String join = String.format("""
                    INNER JOIN (
                        SELECT DISTINCT accession, unp_start, unp_end
                        FROM %s
                        WHERE pdb_id = LOWER(:%s)
                    ) s%d ON m.accession = s%d.accession
                """, structureTable, paramName, paramIndex, paramIndex);
                yield new IdentifierCondition(
                        join,
                        "m.protein_position BETWEEN s" + paramIndex + ".unp_start AND s" + paramIndex + ".unp_end"
                );
            }

            case REFSEQ -> buildRefseqCondition(value, parameters, paramIndex);

            default -> new IdentifierCondition(null, "1=1");
        };
    }

    /**
     * Builds condition for multiple identifier values of the same type.
     */
    private IdentifierCondition buildMultiIdentifierCondition(
            List<String> values,
            SearchType type,
            MapSqlParameterSource parameters,
            int startParamIndex) {

        return switch (type) {
            case UNIPROT -> {
                String paramName = "ids" + startParamIndex;
                parameters.addValue(paramName, values);
                yield new IdentifierCondition(null, "m.accession IN (:" + paramName + ")");
            }

            case GENE -> {
                String paramName = "ids" + startParamIndex;
                parameters.addValue(paramName, values);
                yield new IdentifierCondition(null, "m.gene_name IN (:" + paramName + ")");
            }

            case ENSEMBL -> {
                // Build OR condition for multiple Ensembl IDs
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    IdentifierCondition cond = buildEnsemblCondition(
                            values.get(i), parameters, startParamIndex + i
                    );
                    conditions.add(cond.whereClause);
                }
                yield new IdentifierCondition(null, "(" + String.join(" OR ", conditions) + ")");
            }

            case PDB -> {
                // Multiple PDB structures
                String paramName = "ids" + startParamIndex;
                parameters.addValue(paramName, values.stream()
                        .map(String::toLowerCase)
                        .toList());
                String join = String.format("""
                    INNER JOIN (
                        SELECT DISTINCT accession, unp_start, unp_end
                        FROM %s
                        WHERE pdb_id IN (:%s)
                    ) s%d ON m.accession = s%d.accession
                """, structureTable, paramName, startParamIndex, startParamIndex);
                yield new IdentifierCondition(
                        join,
                        "m.protein_position BETWEEN s" + startParamIndex + ".unp_start AND s" + startParamIndex + ".unp_end"
                );
            }

            case REFSEQ -> {
                // Build OR condition for multiple RefSeq IDs
                List<String> joins = new ArrayList<>();
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < values.size(); i++) {
                    IdentifierCondition cond = buildRefseqCondition(
                            values.get(i), parameters, startParamIndex + i
                    );
                    if (cond.join != null) {
                        joins.add(cond.join);
                    }
                    conditions.add(cond.whereClause);
                }
                yield new IdentifierCondition(
                        String.join("\n", joins),
                        "(" + String.join(" OR ", conditions) + ")"
                );
            }

            default -> new IdentifierCondition(null, "1=1");
        };
    }

    private IdentifierCondition buildEnsemblCondition(
            String value,
            MapSqlParameterSource parameters,
            int paramIndex) {

        Matcher matcher = Pattern.compile("^(ENS[GTPE])(\\d{11})(\\.\\d+)?$", Pattern.CASE_INSENSITIVE)
                .matcher(value);

        if (!matcher.matches()) {
            return new IdentifierCondition(null, "1=0"); // Invalid format - exclude
        }

        String prefix = matcher.group(1).toUpperCase();
        String baseId = prefix + matcher.group(2);
        String versionStr = matcher.group(3);
        String version = versionStr != null ? versionStr.substring(1) : null;

        String idColumn = switch (prefix) {
            case "ENSG" -> "ensg";
            case "ENST" -> "enst";
            case "ENSP" -> "ensp";
            default -> null;
        };

        if (idColumn == null) {
            return new IdentifierCondition(null, "1=0"); // Unsupported type
        }

        String idParam = "ensId" + paramIndex;
        parameters.addValue(idParam, baseId);

        String condition = "m." + idColumn + " = :" + idParam;

        if (version != null) {
            String versionParam = "ensVer" + paramIndex;
            parameters.addValue(versionParam, version);
            condition += " AND m." + idColumn + "v = :" + versionParam;
        }

        return new IdentifierCondition(null, condition);
    }

    private IdentifierCondition buildRefseqCondition(
            String value,
            MapSqlParameterSource parameters,
            int paramIndex) {

        Matcher matcher = SearchTypeResolver.REFSEQ_REGEX.matcher(value);
        boolean hasVersion = matcher.matches() && matcher.group(2) != null;

        String paramName = "refseq" + paramIndex;
        String whereClause = hasVersion
                ? "r" + paramIndex + ".refseq_acc = :" + paramName
                : "r" + paramIndex + ".refseq_acc ILIKE :" + paramName + " || '.%%'";

        parameters.addValue(paramName, value);

        String join = String.format("""
            INNER JOIN (
                SELECT DISTINCT uniprot_acc
                FROM %s
                WHERE %s
            ) r%d ON m.accession = r%d.uniprot_acc
        """, uniprotRefseqTable, whereClause, paramIndex, paramIndex);

        return new IdentifierCondition(join, "1=1"); // Condition is in the join
    }

    private void addConditionalJoins(
            StringBuilder sql,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean joinAlleleFreq, boolean joinConservation, boolean joinCodonTable,
            boolean filterExperimentalModel, boolean filterPocket, boolean filterInteract,
            boolean filterStability, boolean filterByCadd, boolean filterByAm,
            boolean filterByPopEve, boolean filterByEsm1b) {

        // 1. Genomic-level joins first (most selective)
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

        if (joinAlleleFreq) {
            sql.append(String.format("""
                INNER JOIN %s af ON
                    af.chr = m.chromosome AND
                    af.pos = m.genomic_position AND
                    af.ref = m.allele AND
                    af.alt = m.alt_allele
            """, alleleFreqTable));
        }

        // 2. Protein-level joins requiring codon calculation
        if (joinCodonTable) {
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

        if (joinPopEve) {
            String joinType = filterByPopEve ? "INNER" : "LEFT";
            // Changed alias from 'r' to 'unp_ref' to avoid conflict with RefSeq input condition
            sql.append(String.format("""
                %s JOIN %s unp_ref ON unp_ref.uniprot_acc = m.accession
                %s JOIN %s popeve ON
                    popeve.refseq_protein = unp_ref.refseq_acc AND
                    popeve.position = m.protein_position AND
                    popeve.wt_aa = m.protein_seq AND
                    popeve.mt_aa = c.amino_acid
            """, joinType, uniprotRefseqTable, joinType, popeveTable));
        }

        if (joinEsm1b) {
            String joinType = filterByEsm1b ? "INNER" : "LEFT";
            sql.append(String.format("""
                %s JOIN %s esm ON
                    esm.accession = m.accession AND
                    esm.position = m.protein_position AND
                    esm.mt_aa = c.amino_acid
            """, joinType, esmTable));
        }

        // 3. Protein-level joins without codon
        if (joinConservation) {
            sql.append(String.format("""
                INNER JOIN %s cons ON
                    cons.accession = m.accession AND
                    cons.position = m.protein_position AND
                    cons.aa = m.protein_seq
            """, conservationTable));
        }

        if (filterExperimentalModel) {
            sql.append(String.format("""
                INNER JOIN %s struct ON
                    struct.accession = m.accession AND
                    m.protein_position >= struct.unp_start AND
                    m.protein_position <= struct.unp_end
            """, structureTable));
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
    }

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
            sql.append(" AND (").append(String.join(" OR ", caddClauses)).append(")");
        }

        // AlphaMissense filter
        if (filterByAm) {
            List<Integer> amValues = request.getAm().stream().map(AmClass::getValue).toList();
            sql.append(" AND am.am_class IN (:amClasses)");
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
            sql.append(" AND (").append(String.join(" OR ", popeveClauses)).append(")");
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
            sql.append(" AND (").append(String.join(" OR ", afClauses)).append(")");
        }

        // Conservation filter
        if (filterByConservation) {
            if (request.getConservationMin() != null) {
                sql.append(" AND cons.score >= :conservationMin");
                parameters.addValue("conservationMin", request.getConservationMin());
            }
            if (request.getConservationMax() != null) {
                sql.append(" AND cons.score <= :conservationMax");
                parameters.addValue("conservationMax", request.getConservationMax());
            }
        }

        // ESM1b filter
        if (filterByEsm1b) {
            if (request.getEsm1bMin() != null) {
                sql.append(" AND esm.score >= :esm1bMin");
                parameters.addValue("esm1bMin", request.getEsm1bMin());
            }
            if (request.getEsm1bMax() != null) {
                sql.append(" AND esm.score <= :esm1bMax");
                parameters.addValue("esm1bMax", request.getEsm1bMax());
            }
        }

        // Stability filter
        if (filterStability) {
            Set<StabilityChange> changes = EnumSet.copyOf(request.getStability());
            if (!changes.equals(EnumSet.allOf(StabilityChange.class))) {
                if (changes.contains(StabilityChange.LIKELY_DESTABILISING)) {
                    sql.append(" AND f.foldx_ddg >= 2");
                } else if (changes.contains(StabilityChange.UNLIKELY_DESTABILISING)) {
                    sql.append(" AND f.foldx_ddg < 2");
                }
            }
        }
    }

    /**
     * Helper class to hold JOIN and WHERE components for identifier conditions.
     */
    private static class IdentifierCondition {
        final String join;        // JOIN clause (may be null)
        final String whereClause; // WHERE condition (never null)

        IdentifierCondition(String join, String whereClause) {
            this.join = join;
            this.whereClause = whereClause;
        }
    }
}
