package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
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
import uk.ac.ebi.protvar.model.Identifier;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.types.IdentifierType;
import uk.ac.ebi.protvar.types.*;
import uk.ac.ebi.protvar.utils.MappingRequestValidator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class GenomicVariantRepo {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenomicVariantRepo.class);

    /**
     * Upper bound on the COUNT(*) for paginated filter-only browse. Counting
     * the full result set (potentially tens of millions of rows) would
     * dominate every request — so we cap with an inner LIMIT and report at
     * most CAP+1 as the total. Clients receive this same value via the
     * {@code totalCap} field on PagedMappingResponse, so they don't need to
     * hard-code it.
     */
    public static final int COUNT_CAP = 10000;

    /**
     * Per-query timeout (seconds) for the bounded COUNT. Even with the
     * inner LIMIT, sparse multi-filter intersections (e.g. pocket=true +
     * known=true) force Postgres to scan a large portion of the input to
     * confirm "no more rows", and counting can run for many minutes. When
     * the timeout fires we abandon the count, return totalItems = -1 (the
     * "unknown" sentinel) and let the FE render "Many results" with
     * Prev/Next-only pagination. The data fetch itself is unaffected.
     */
    private static final int COUNT_TIMEOUT_SECONDS = 3;

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
    @Value("${tbl.interfaces}") private String interactionTable;
    @Value("${tbl.foldx}") private String foldxTable;
    @Value("${tbl.mapping.dbsnp}") private String dbsnpLookupTable;

    // ========================================================================
    // MAIN ENTRY POINT
    // ========================================================================

    public Page<VariantInput> get(MappingRequest request, Pageable pageable) {
        if (pageable == null) {
            LOGGER.warn("Defaulting to page {}, size {}.", 0+1, PageUtils.DEFAULT_PAGE_SIZE);
            pageable = PageRequest.of(0, PageUtils.DEFAULT_PAGE_SIZE);
        }
        boolean isDownload = request instanceof DownloadRequest;

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

        boolean joinCadd = filterByCadd || sortByCadd;
        boolean joinAm = filterByAm || sortByAm;
        boolean joinPopEve = filterByPopEve || sortByPopEve;
        boolean joinEsm1b = filterByEsm1b || sortByEsm1b;
        boolean joinCodonTable = joinAm || joinPopEve || joinEsm1b || filterStability;

        StringBuilder query = new StringBuilder();

        boolean hasIdentifiers = request.getIds() != null && !request.getIds().isEmpty();

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
            boolean hasAnyFilter = filterKnown || filterByCadd || filterByAm || filterByPopEve
                    || filterByAlleleFreq || filterByConservation || filterByEsm1b
                    || filterExperimentalModel || filterPocket || filterInteract || filterStability;

            // Defence in depth: MappingRequestValidator should have rejected this
            // request at the controller layer with a clean 400. If we reach here
            // without a driver filter (pocket/interact/experimentalModel/known),
            // a downstream caller bypassed validation.
            if (!hasAnyFilter) {
                throw new IllegalArgumentException(MappingRequestValidator.NO_DRIVER_MESSAGE);
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
                buildQueryWithDbsnpFilter(query, parameters,
                        joinCodonTable, joinCadd, joinAm, joinPopEve, joinEsm1b,
                        filterByAlleleFreq, filterByConservation, filterStability,
                        filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                        request);
            } else {
                // STRATEGY 4: No highly selective filters - start with most selective score filter
                buildQueryWithScoreFilters(query, parameters,
                        joinCodonTable, joinCadd, joinAm, joinPopEve, joinEsm1b,
                        filterByAlleleFreq, filterByConservation, filterStability,
                        filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                        request);
            }
        }

        long total = -1;
        if (!isDownload) {
            // Cap the COUNT to avoid scanning the full filter-only result set,
            // which can be tens of millions of rows. Inner LIMIT lets Postgres
            // short-circuit once CAP+1 rows are seen. A returned total of
            // CAP+1 means "more than CAP" — clients should treat it as such.
            //
            // For sparse multi-filter intersections the bounded COUNT can
            // still be slow, so it runs under a per-query timeout. On
            // timeout we leave total = -1 (the "unknown" sentinel) and
            // the data fetch below proceeds normally.
            String countSql = "SELECT COUNT(*) FROM (\n" + query + "\nLIMIT " + (COUNT_CAP + 1) + "\n) cnt";
            try {
                total = countWithTimeout(countSql, parameters, COUNT_TIMEOUT_SECONDS);
            } catch (DataAccessException e) {
                LOGGER.info("Filter-only COUNT exceeded {}s timeout; reporting unknown total", COUNT_TIMEOUT_SECONDS);
                total = -1;
            }

            if (total == 0) {
                return Page.empty(pageable);
            }
        }

        // ORDER BY is emitted only when the user explicitly requested a sort.
        // For unsorted requests we rely on the leading-table iteration order
        // (deterministic for a given query plan); adding an explicit ORDER BY
        // would block LIMIT pushdown and force a full intermediate sort.
        // Sort-by-score paths are still slow until score-leading strategies
        // land — see follow-up.
        boolean userRequestedSort = sortByCadd || sortByAm || sortByPopEve || sortByEsm1b;
        if (userRequestedSort) {
            String sortOrder = "asc".equalsIgnoreCase(request.getOrder()) ? "ASC" : "DESC";
            query.append("\nORDER BY ");
            if (sortByCadd) {
                query.append("cadd.score ").append(sortOrder).append(", ");
            } else if (sortByAm) {
                query.append("am.am_pathogenicity ").append(sortOrder).append(", ");
            } else if (sortByPopEve) {
                query.append("popeve.popeve ").append(sortOrder).append(", ");
            } else if (sortByEsm1b) {
                query.append("esm.score ").append(sortOrder).append(", ");
            }
            query.append("m.protein_position, m.codon_position, bases.alt_allele\n");
        }

        query.append("LIMIT :pageSize OFFSET :offset");
        parameters.addValue("pageSize", pageable.getPageSize());
        parameters.addValue("offset", pageable.getOffset());

        List<VariantInput> variants = jdbcTemplate.query(query.toString(), parameters,
                (rs, rowNum) -> {
                    String chr = rs.getString("chromosome");
                    int pos = rs.getInt("genomic_position");
                    String ref = rs.getString("allele");
                    String alt = rs.getString("alt_allele");
                    return new GenomicInput(
                            String.format("%s %d %s %s", chr, pos, ref, alt),
                            chr, pos, ref, alt
                    );
                });

        if (isDownload) {
            return new PageImpl<>(variants);
        }

        if (total < 0) {
            // COUNT timed out: total is unknown. Spring's PageImpl computes
            // isLast() / totalPages() from total, which would be wrong here.
            // We derive isLast from the number of rows returned: if we got
            // fewer than the requested page size, we're on the last page.
            final boolean isLast = variants.size() < pageable.getPageSize();
            final boolean hasPrevious = pageable.getPageNumber() > 0;
            return new PageImpl<VariantInput>(variants, pageable, 0) {
                @Override public long getTotalElements() { return -1L; }
                @Override public int  getTotalPages()    { return 0; }
                @Override public boolean isLast()        { return isLast; }
                @Override public boolean hasNext()       { return !isLast; }
                @Override public boolean hasPrevious()   { return hasPrevious; }
            };
        }

        return new PageImpl<>(variants, pageable, total);
    }

    /**
     * Run a COUNT(*) query with a per-statement timeout. Wraps the shared
     * NamedParameterJdbcTemplate's underlying DataSource in a fresh
     * JdbcTemplate so the timeout is local to this call and doesn't leak
     * into other queries. Throws QueryTimeoutException (subclass of
     * DataAccessException) when the timeout fires.
     */
    private long countWithTimeout(String sql, MapSqlParameterSource params, int timeoutSeconds) {
        org.springframework.jdbc.core.JdbcTemplate scoped =
                new org.springframework.jdbc.core.JdbcTemplate(jdbcTemplate.getJdbcTemplate().getDataSource());
        scoped.setQueryTimeout(timeoutSeconds);
        return new NamedParameterJdbcTemplate(scoped).queryForObject(sql, params, Long.class);
    }

    // ========================================================================
    // STRATEGY 1: Identifier filtering
    // ========================================================================

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

        buildIdentifierCTEsAndBranches(ctes, unionBranches, parameters, request.getIds());

        if (!ctes.isEmpty()) {
            query.append("WITH ").append(String.join(",\n", ctes)).append(",\n");
        } else {
            query.append("WITH ");
        }

        query.append("filtered_mapping AS (\n");
        query.append(String.join("\n\nUNION\n\n", unionBranches));
        query.append("\n)");

        boolean hasFeatureFilters = filterPocket || filterInteract || filterExperimentalModel;

        if (hasFeatureFilters) {
            query.append(",\nfeature_filtered AS (\n");
            query.append("  SELECT fm.*\n");
            query.append("  FROM filtered_mapping fm\n");

            if (filterPocket) {
                query.append("""
                        INNER JOIN (
                            SELECT DISTINCT struct_id, unnest(pocket_resid) as position
                            FROM %s
                        ) p ON p.struct_id = fm.accession AND p.position = fm.protein_position
                        """.formatted(pocketTable));
            }

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

            if (filterExperimentalModel) {
                query.append("""
                        INNER JOIN (
                          SELECT DISTINCT accession, unp_start, unp_end
                          FROM %s
                        ) struct ON struct.accession = fm.accession
                          AND fm.protein_position BETWEEN struct.unp_start AND struct.unp_end
                        """.formatted(structureTable));
            }

            query.append("\n)");
        }

        query.append("\nSELECT DISTINCT\n");
        query.append("  m.chromosome, m.genomic_position, m.allele, bases.alt_allele,\n");
        query.append("  m.protein_position, m.codon_position");

        if (joinCadd) query.append(", cadd.score");
        if (joinAm) query.append(", am.am_pathogenicity");
        if (joinPopEve) query.append(", popeve.popeve");
        if (joinEsm1b) query.append(", esm.score");

        String sourceTable = hasFeatureFilters ? "feature_filtered" : "filtered_mapping";

        query.append("\nFROM ").append(sourceTable).append(" m\n");
        query.append("JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS bases(alt_allele)\n");
        query.append("  ON bases.alt_allele <> m.allele\n");

        if (joinCodonTable) {
            query.append("""
                    LEFT JOIN codon_table c ON c.codon = UPPER(CASE
                      WHEN m.codon_position = 1 THEN rna_base_for_strand(bases.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
                      WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(bases.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
                      WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(bases.alt_allele, m.reverse_strand)
                      ELSE m.codon
                    END)
                    """);
        }

        addRemainingJoins(query, filterKnown, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                "bases.alt_allele");

        query.append("WHERE 1=1\n");
        addFilters(query, parameters, request,
                filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                filterByConservation, filterByEsm1b, filterStability);
    }

    // ========================================================================
    // STRATEGY 2: Feature filters (pocket/interact/structure) as leading join
    // ========================================================================

    private void buildQueryWithFeatureFilters(
            StringBuilder query, MapSqlParameterSource parameters,
            boolean filterPocket, boolean filterInteract, boolean filterExperimentalModel,
            boolean filterKnown, boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        query.append("WITH feature_positions AS (\n");

        List<String> featureUnions = new ArrayList<>();

        if (filterPocket) {
            featureUnions.add("""
          SELECT DISTINCT struct_id as accession, unnest(pocket_resid) as position
          FROM %s
        """.formatted(pocketTable));
        }

        if (filterInteract) {
            featureUnions.add("""
          SELECT accession, position FROM (
              SELECT a as accession, unnest(a_residues) as position FROM %s
              UNION ALL
              SELECT b as accession, unnest(b_residues) as position FROM %s
          ) i
        """.formatted(interactionTable, interactionTable));
        }

        if (filterExperimentalModel) {
            featureUnions.add("""
          SELECT s.accession, pos as position
          FROM %s s
          CROSS JOIN generate_series(s.unp_start, s.unp_end) as pos
        """.formatted(structureTable));
        }

        query.append(String.join("\n  UNION\n", featureUnions));
        query.append("\n)\n");

        // Lead FROM feature_positions (≤547K) and join mapping inner with
        // is_canonical pushed into the JOIN — lets the planner use the
        // (accession, protein_position) index and avoids a full scan of the
        // 169M-row mapping table. is_canonical also makes rows naturally
        // unique per (chr, pos, allele, alt_allele) so SELECT DISTINCT is
        // unnecessary, and dropping it lets LIMIT pushdown work.
        query.append("SELECT\n");
        query.append("  m.chromosome, m.genomic_position, m.allele, bases.alt_allele,\n");
        query.append("  m.protein_position, m.codon_position");

        if (joinCadd) query.append(",\n  cadd.score");
        if (joinAm) query.append(",\n  am.am_pathogenicity");
        if (joinPopEve) query.append(",\n  popeve.popeve");
        if (joinEsm1b) query.append(",\n  esm.score");

        query.append("\nFROM feature_positions fp\n");
        query.append("INNER JOIN ").append(mappingTable).append(" m\n");
        query.append("  ON m.accession = fp.accession\n");
        query.append("  AND m.protein_position = fp.position\n");
        query.append("  AND m.is_canonical = true\n");
        query.append("JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS bases(alt_allele)\n");
        query.append("  ON bases.alt_allele <> m.allele\n");

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(bases.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(bases.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(bases.alt_allele, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        addRemainingJoins(query, filterKnown, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                "bases.alt_allele");

        query.append("WHERE 1=1\n");
        addFilters(query, parameters, request,
                filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                filterByConservation, filterByEsm1b, filterStability);
    }

    // ========================================================================
    // STRATEGY 3: dbSNP pre-join as leading table (known variants only)
    // ========================================================================

    private void buildQueryWithDbsnpFilter(
            StringBuilder query, MapSqlParameterSource parameters,
            boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        // Lead FROM dbsnp_lookup (~15M) ordered by (chr, pos), then expand
        // alts and join mapping inner. is_canonical pushed into the JOIN ON
        // makes rows unique per (chr, pos, ref, alt) so SELECT DISTINCT is
        // unnecessary; dropping it enables LIMIT pushdown.
        query.append("SELECT\n");
        query.append("  m.chromosome, m.genomic_position, m.allele, dbsnp_alts.alt_allele,\n");
        query.append("  m.protein_position, m.codon_position");

        if (joinCadd) query.append(",\n  cadd.score");
        if (joinAm) query.append(",\n  am.am_pathogenicity");
        if (joinPopEve) query.append(",\n  popeve.popeve");
        if (joinEsm1b) query.append(",\n  esm.score");

        query.append("\nFROM ").append(dbsnpLookupTable).append(" d\n");
        query.append("CROSS JOIN LATERAL unnest(d.known_alts) AS dbsnp_alts(alt_allele)\n");
        query.append("INNER JOIN ").append(mappingTable).append(" m\n");
        query.append("  ON m.chromosome = d.chr\n");
        query.append("  AND m.genomic_position = d.pos\n");
        query.append("  AND m.allele = d.ref\n");
        query.append("  AND m.is_canonical = true\n");

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(dbsnp_alts.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(dbsnp_alts.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(dbsnp_alts.alt_allele, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        // filterKnown=false: already starting from dbSNP table.
        // dbsnp_alts.alt_allele comes from the LATERAL unnest of d.known_alts
        // — Strategy 3 has no plain `bases` VALUES cross-join, so any
        // downstream join referencing the alt allele must use this name.
        addRemainingJoins(query, false, filterByAlleleFreq, filterByConservation,
                joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                "dbsnp_alts.alt_allele");

        query.append("WHERE 1=1\n");
        addFilters(query, parameters, request,
                filterByCadd, filterByAm, filterByPopEve, filterByAlleleFreq,
                filterByConservation, filterByEsm1b, filterStability);
    }

    // ========================================================================
    // STRATEGY 4: Score-first (alleleFreq or conservation as leading filter)
    // ========================================================================

    private void buildQueryWithScoreFilters(
            StringBuilder query, MapSqlParameterSource parameters,
            boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByAlleleFreq, boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        if (filterByAlleleFreq) {
            buildQueryStartingWithAlleleFreq(query, parameters, joinCodonTable,
                    joinCadd, joinAm, joinPopEve, joinEsm1b,
                    filterByConservation, filterStability,
                    filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                    request);
        } else if (filterByConservation) {
            buildQueryStartingWithConservation(query, parameters, joinCodonTable,
                    joinCadd, joinAm, joinPopEve, joinEsm1b,
                    filterStability,
                    filterByCadd, filterByAm, filterByPopEve, filterByEsm1b,
                    request);
        }
        // CADD/AM/PopEVE/ESM1b-only filter-only queries are blocked by safety check above.
        // Those score tables are too large to scan without a more selective anchor.
    }

    private void buildQueryStartingWithAlleleFreq(
            StringBuilder query, MapSqlParameterSource parameters,
            boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterByConservation, boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        List<String> afClauses = new ArrayList<>();
        for (int i = 0; i < request.getAlleleFreq().size(); i++) {
            AlleleFreqCategory category = request.getAlleleFreq().get(i);
            String minParam = "afMin" + i;
            String maxParam = "afMax" + i;
            afClauses.add("(af.af >= :" + minParam + " AND af.af < :" + maxParam + ")");
            parameters.addValue(minParam, category.getMin());
            parameters.addValue(maxParam, category.getMax());
        }

        // Lead FROM gnomad_allele_freq, then join mapping inner with
        // is_canonical pushed into the JOIN ON. SELECT DISTINCT is dropped
        // because canonical-only rows are unique per (chr, pos, ref, alt);
        // this also enables LIMIT pushdown.
        query.append("SELECT\n");
        query.append("  m.chromosome, m.genomic_position, m.allele, af.alt as alt_allele,\n");
        query.append("  m.protein_position, m.codon_position");

        if (joinCadd) query.append(",\n  cadd.score");
        if (joinAm) query.append(",\n  am.am_pathogenicity");
        if (joinPopEve) query.append(",\n  popeve.popeve");
        if (joinEsm1b) query.append(",\n  esm.score");

        query.append("\nFROM ").append(alleleFreqTable).append(" af\n");
        query.append("INNER JOIN ").append(mappingTable).append(" m\n");
        query.append("  ON m.chromosome = af.chr\n");
        query.append("  AND m.genomic_position = af.pos\n");
        query.append("  AND m.allele = af.ref\n");
        query.append("  AND m.is_canonical = true\n");

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(af.alt, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(af.alt, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(af.alt, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        if (filterByConservation) {
            query.append("""
                INNER JOIN %s cons ON cons.accession = m.accession
                  AND cons.position = m.protein_position
                  AND cons.aa = m.protein_seq
                """.formatted(conservationTable));
        }

        addScoreTableJoins(query, joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b, "af.alt");

        query.append("WHERE (").append(String.join(" OR ", afClauses)).append(")\n");
        addFiltersExceptAlleleFreq(query, parameters, request,
                filterByCadd, filterByAm, filterByPopEve,
                filterByConservation, filterByEsm1b, filterStability);
    }

    private void buildQueryStartingWithConservation(
            StringBuilder query, MapSqlParameterSource parameters,
            boolean joinCodonTable,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByEsm1b,
            MappingRequest request) {

        // Lead FROM conserv_score (~14M), then join mapping inner with
        // is_canonical pushed into the JOIN ON. SELECT DISTINCT dropped for
        // LIMIT pushdown; canonical rows are unique per (chr, pos, allele,
        // alt_allele) after the bases cross join.
        query.append("SELECT\n");
        query.append("  m.chromosome, m.genomic_position, m.allele, bases.alt_allele,\n");
        query.append("  m.protein_position, m.codon_position");

        if (joinCadd) query.append(",\n  cadd.score");
        if (joinAm) query.append(",\n  am.am_pathogenicity");
        if (joinPopEve) query.append(",\n  popeve.popeve");
        if (joinEsm1b) query.append(",\n  esm.score");

        query.append("\nFROM ").append(conservationTable).append(" cons\n");
        query.append("INNER JOIN ").append(mappingTable).append(" m\n");
        query.append("  ON m.accession = cons.accession\n");
        query.append("  AND m.protein_position = cons.position\n");
        query.append("  AND m.protein_seq = cons.aa\n");
        query.append("  AND m.is_canonical = true\n");
        query.append("JOIN (VALUES ('A'), ('T'), ('G'), ('C')) AS bases(alt_allele)\n");
        query.append("  ON bases.alt_allele <> m.allele\n");

        if (joinCodonTable) {
            query.append("""
          LEFT JOIN codon_table c ON c.codon = UPPER(CASE
              WHEN m.codon_position = 1 THEN rna_base_for_strand(bases.alt_allele, m.reverse_strand) || substring(m.codon, 2, 2)
              WHEN m.codon_position = 2 THEN substring(m.codon, 1, 1) || rna_base_for_strand(bases.alt_allele, m.reverse_strand) || substring(m.codon, 3, 1)
              WHEN m.codon_position = 3 THEN substring(m.codon, 1, 2) || rna_base_for_strand(bases.alt_allele, m.reverse_strand)
              ELSE m.codon
          END)
        """);
        }

        addScoreTableJoins(query, joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b, "bases.alt_allele");

        query.append("WHERE 1=1\n");
        if (request.getConservationMin() != null) {
            query.append("AND cons.score >= :conservationMin\n");
            parameters.addValue("conservationMin", request.getConservationMin());
        }
        if (request.getConservationMax() != null) {
            query.append("AND cons.score <= :conservationMax\n");
            parameters.addValue("conservationMax", request.getConservationMax());
        }
        addFiltersExceptConservation(query, parameters, request,
                filterByCadd, filterByAm, filterByPopEve, false,
                filterByEsm1b, filterStability);
    }

    // ========================================================================
    // IDENTIFIER CTE BUILDING
    // ========================================================================

    private void buildIdentifierCTEsAndBranches(List<String> ctes, List<String> unionBranches,
                                                MapSqlParameterSource parameters,
                                                List<Identifier> ids) {
        Map<IdentifierType, List<String>> identifiersByType = ids.stream()
                .collect(Collectors.groupingBy(
                        Identifier::type,
                        Collectors.mapping(Identifier::value, Collectors.toList())
                ));

        List<String> refseqIds = identifiersByType.get(IdentifierType.REFSEQ);
        List<String> pdbIds = identifiersByType.get(IdentifierType.PDB);
        List<String> uniprotIds = identifiersByType.get(IdentifierType.UNIPROT);
        List<String> geneIds = identifiersByType.get(IdentifierType.GENE);
        List<String> ensemblIds = identifiersByType.get(IdentifierType.ENSEMBL);

        if (refseqIds != null && !refseqIds.isEmpty()) {
            if (refseqIds.size() == 1) {
                String refseqAcc = refseqIds.get(0);
                ctes.add("""
                    refseq_acc AS (
                        SELECT DISTINCT r.uniprot_acc
                        FROM %s r
                        WHERE r.refseq_acc %s
                    )
                    """.formatted(
                    uniprotRefseqTable,
                    refseqAcc.contains(".") ? "= :refseqAcc" : "LIKE :refseqAcc || '.%%'"));
                parameters.addValue("refseqAcc", refseqAcc);
            } else {
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

    private List<String> buildEnsemblConditions(List<String> ensemblIds, MapSqlParameterSource parameters) {
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

    // ========================================================================
    // SHARED JOIN / FILTER HELPERS
    // ========================================================================

    /**
     * @param altAlleleExpr  SQL expression that yields the alternate allele
     *                       in the strategy's join scope. Strategies 1 and 2
     *                       pass {@code "bases.alt_allele"} (from the
     *                       VALUES bases cross-join). Strategy 3 (dbSNP)
     *                       passes {@code "dbsnp_alts.alt_allele"} (from
     *                       its LATERAL unnest of d.known_alts).
     */
    private void addRemainingJoins(
            StringBuilder query,
            boolean filterKnown,
            boolean filterByAlleleFreq, boolean filterByConservation,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByEsm1b,
            String altAlleleExpr) {

        if (filterKnown) {
            query.append("""
                    INNER JOIN %s d ON d.chr = m.chromosome
                      AND d.pos = m.genomic_position
                      AND d.ref = m.allele
                      AND %s = ANY(d.known_alts)
                    """.formatted(dbsnpLookupTable, altAlleleExpr));
        }

        if (filterByConservation) {
            query.append("""
                    INNER JOIN %s cons ON cons.accession = m.accession
                      AND cons.position = m.protein_position
                      AND cons.aa = m.protein_seq
                    """.formatted(conservationTable));
        }

        if (filterByAlleleFreq) {
            query.append("""
                    INNER JOIN %s af ON af.chr = m.chromosome
                      AND af.pos = m.genomic_position
                      AND af.ref = m.allele
                      AND af.alt = %s
                    """.formatted(alleleFreqTable, altAlleleExpr));
        }

        addScoreTableJoins(query, joinCadd, joinAm, joinPopEve, joinEsm1b, filterStability,
                filterByCadd, filterByAm, filterByPopEve, filterByEsm1b, altAlleleExpr);
    }

    private void addScoreTableJoins(
            StringBuilder query,
            boolean joinCadd, boolean joinAm, boolean joinPopEve, boolean joinEsm1b,
            boolean filterStability,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByEsm1b,
            String altAlleleExpr) {

        if (joinCadd) {
            String joinType = filterByCadd ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s cadd ON cadd.chromosome = m.chromosome
                  AND cadd.position = m.genomic_position
                  AND cadd.reference_allele = m.allele
                  AND cadd.alt_allele = %s
                """.formatted(joinType, caddTable, altAlleleExpr));
        }

        if (joinAm) {
            String joinType = filterByAm ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s am ON am.accession = m.accession
                  AND am.position = m.protein_position
                  AND am.wt_aa = m.protein_seq
                  AND am.mt_aa = c.amino_acid
                """.formatted(joinType, amTable));
        }

        if (joinPopEve) {
            String joinType = filterByPopEve ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s unp_ref ON unp_ref.uniprot_acc = m.accession
                %s JOIN %s popeve ON popeve.refseq_protein = unp_ref.refseq_acc
                  AND popeve.position = m.protein_position
                  AND popeve.wt_aa = m.protein_seq
                  AND popeve.mt_aa = c.amino_acid
                """.formatted(joinType, uniprotRefseqTable, joinType, popeveTable));
        }

        if (joinEsm1b) {
            String joinType = filterByEsm1b ? "INNER" : "LEFT";
            query.append("""
                %s JOIN %s esm ON esm.accession = m.accession
                  AND esm.position = m.protein_position
                  AND esm.mt_aa = c.amino_acid
                """.formatted(joinType, esmTable));
        }

        if (filterStability) {
            query.append("""
                INNER JOIN %s f ON f.protein_acc = m.accession
                  AND f.position = m.protein_position
                  AND f.mutated_type = c.amino_acid
                """.formatted(foldxTable));
        }
    }

    private void addFilters(
            StringBuilder sql, MapSqlParameterSource parameters, MappingRequest request,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByAlleleFreq, boolean filterByConservation,
            boolean filterByEsm1b, boolean filterStability) {

        if (filterByCadd) {
            List<String> caddClauses = new ArrayList<>();
            for (int i = 0; i < request.getCadd().size(); i++) {
                CaddCategory category = request.getCadd().get(i);
                caddClauses.add("(cadd.score >= :caddMin" + i + " AND cadd.score < :caddMax" + i + ")");
                parameters.addValue("caddMin" + i, category.getMin());
                parameters.addValue("caddMax" + i, category.getMax());
            }
            sql.append("AND (").append(String.join(" OR ", caddClauses)).append(")\n");
        }

        if (filterByAm) {
            Integer[] amValues = request.getAm().stream()
                    .map(AmClass::getValue)
                    .toArray(Integer[]::new);
            sql.append("AND am.am_class = ANY(:amClasses)\n");
            parameters.addValue("amClasses", amValues);
        }

        if (filterByPopEve) {
            List<String> popeveClauses = new ArrayList<>();
            for (int i = 0; i < request.getPopeve().size(); i++) {
                PopEveClass category = request.getPopeve().get(i);
                if (Double.isInfinite(category.getMin()) && category.getMin() < 0) {
                    popeveClauses.add("(popeve.popeve < :popeveMax" + i + ")");
                    parameters.addValue("popeveMax" + i, category.getMax());
                } else if (Double.isInfinite(category.getMax())) {
                    popeveClauses.add("(popeve.popeve >= :popeveMin" + i + ")");
                    parameters.addValue("popeveMin" + i, category.getMin());
                } else {
                    popeveClauses.add("(popeve.popeve >= :popeveMin" + i + " AND popeve.popeve < :popeveMax" + i + ")");
                    parameters.addValue("popeveMin" + i, category.getMin());
                    parameters.addValue("popeveMax" + i, category.getMax());
                }
            }
            sql.append("AND (").append(String.join(" OR ", popeveClauses)).append(")\n");
        }

        if (filterByAlleleFreq) {
            List<String> afClauses = new ArrayList<>();
            for (int i = 0; i < request.getAlleleFreq().size(); i++) {
                AlleleFreqCategory category = request.getAlleleFreq().get(i);
                afClauses.add("(af.af >= :afMin" + i + " AND af.af < :afMax" + i + ")");
                parameters.addValue("afMin" + i, category.getMin());
                parameters.addValue("afMax" + i, category.getMax());
            }
            sql.append("AND (").append(String.join(" OR ", afClauses)).append(")\n");
        }

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

    private void addFiltersExceptAlleleFreq(
            StringBuilder sql, MapSqlParameterSource parameters, MappingRequest request,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve,
            boolean filterByConservation, boolean filterByEsm1b, boolean filterStability) {
        addFilters(sql, parameters, request,
                filterByCadd, filterByAm, filterByPopEve,
                false, filterByConservation, filterByEsm1b, filterStability);
    }

    private void addFiltersExceptConservation(
            StringBuilder sql, MapSqlParameterSource parameters, MappingRequest request,
            boolean filterByCadd, boolean filterByAm, boolean filterByPopEve, boolean filterByAlleleFreq,
            boolean filterByEsm1b, boolean filterStability) {
        addFilters(sql, parameters, request,
                filterByCadd, filterByAm, filterByPopEve,
                filterByAlleleFreq, false, filterByEsm1b, filterStability);
    }

    private <T> boolean isFilteringRequired(List<T> categories) {
        return categories != null && !categories.isEmpty();
    }
}
