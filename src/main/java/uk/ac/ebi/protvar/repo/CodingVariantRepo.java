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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.constants.PageUtils;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.model.DownloadRequest;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.types.AlleleFreqCategory;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;
import uk.ac.ebi.protvar.types.PopEveClass;
import uk.ac.ebi.protvar.types.StabilityChange;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Filter/sort over the precomputed {@code rel_<R>_coding_variant} table: one row per mapped coding position ×
 * alt on the gene's lead accession, with every filterable and sortable attribute already on the row (design:
 * doc/coding_variant_table.md). Every filter is a plain column condition, so there are no per-request joins.
 *
 * <p>Same contract as {@link GenomicVariantRepo#get}: a page of genomic variants plus a capped count
 * ({@link GenomicVariantRepo#COUNT_CAP}+1 = "more than the cap", -1 = count timed out).
 *
 * <p>Score sorts show only variants that have that score ({@code <score> IS NOT NULL}), so the planner reads the
 * score's btree in order and stops after one page. The default order is (accession, protein_pos), served by
 * its index, which keeps pages stable and groups results per protein.
 */
@Repository
@RequiredArgsConstructor
public class CodingVariantRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodingVariantRepo.class);
    private static final int COUNT_TIMEOUT_SECONDS = 3;

    // request sort key → column
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "cadd", "cadd", "avi", "avi", "am", "am", "popeve", "popeve", "esm1b", "esm1b");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.coding.variant}") private String table;

    public Page<VariantInput> get(MappingRequest request, Pageable pageable) {
        if (pageable == null) pageable = PageRequest.of(0, PageUtils.DEFAULT_PAGE_SIZE);
        boolean isDownload = request instanceof DownloadRequest;

        MapSqlParameterSource params = new MapSqlParameterSource();
        // filter-only browse is canonical-only today; including non-canonical-only positions is an open
        // decision (design §8) and just drops this condition
        List<String> where = new ArrayList<>(List.of("is_canonical"));
        where.addAll(filters(request, params));

        String sortColumn = request.getSort() == null ? null : SORT_COLUMNS.get(request.getSort().toLowerCase());
        if (sortColumn != null) where.add(sortColumn + " IS NOT NULL");
        String from = "FROM " + table + "\nWHERE " + String.join("\n  AND ", where);

        long total = -1;
        if (!isDownload) {
            String countSql = "SELECT count(*) FROM (SELECT 1 " + from + " LIMIT " + (GenomicVariantRepo.COUNT_CAP + 1) + ") c";
            try {
                total = withTimeout(COUNT_TIMEOUT_SECONDS).queryForObject(countSql, params, Long.class);
            } catch (DataAccessException e) {
                LOGGER.info("coding_variant COUNT exceeded {}s timeout; reporting unknown total", COUNT_TIMEOUT_SECONDS);
            }
            if (total == 0) return Page.empty(pageable);
        }

        String order = sortColumn != null
                ? sortColumn + ("asc".equalsIgnoreCase(request.getOrder()) ? " ASC" : " DESC")
                  + ", chromosome, position, alt, accession"
                : "accession, protein_pos, position, alt";
        String sql = "SELECT chromosome, position, ref, alt " + from
                + "\nORDER BY " + order + "\nLIMIT :pageSize OFFSET :offset";
        params.addValue("pageSize", pageable.getPageSize()).addValue("offset", pageable.getOffset());

        List<VariantInput> variants = jdbcTemplate.query(sql, params, (rs, i) -> {
            String chr = rs.getString("chromosome");
            int pos = rs.getInt("position");
            String ref = rs.getString("ref");
            String alt = rs.getString("alt");
            return new GenomicInput(String.format("%s %d %s %s", chr, pos, ref, alt), chr, pos, ref, alt);
        });

        if (isDownload) return new PageImpl<>(variants);
        if (total < 0) {
            // count timed out: derive isLast from the page, as GenomicVariantRepo does
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
     * One condition per active filter (AND across filters, OR within a band list), on the table's columns.
     * Same semantics as GenomicVariantRepo/MappingRepo: band lists are [min, max) ORs; popEVE bands may be
     * open-ended; stability means "any FoldX row" in either direction, hence max/min ΔΔG.
     */
    List<String> filters(MappingRequest r, MapSqlParameterSource params) {
        List<String> c = new ArrayList<>();
        if (Boolean.TRUE.equals(r.getKnown())) c.add("known");

        if (notEmpty(r.getCadd())) {
            List<String> or = new ArrayList<>();
            for (int i = 0; i < r.getCadd().size(); i++) {
                CaddCategory cat = r.getCadd().get(i);
                or.add("(cadd >= :caddMin" + i + " AND cadd < :caddMax" + i + ")");
                params.addValue("caddMin" + i, cat.getMin()).addValue("caddMax" + i, cat.getMax());
            }
            c.add("(" + String.join(" OR ", or) + ")");
        }
        if (r.getAviMin() != null) {
            c.add("avi >= :aviMin");
            params.addValue("aviMin", r.getAviMin());
        }
        if (notEmpty(r.getAm())) {
            c.add("am_class = ANY(:amClasses)");
            params.addValue("amClasses", r.getAm().stream().map(AmClass::getValue).toArray(Integer[]::new));
        }
        if (notEmpty(r.getPopeve())) {
            List<String> or = new ArrayList<>();
            for (int i = 0; i < r.getPopeve().size(); i++) {
                PopEveClass cat = r.getPopeve().get(i);
                if (Double.isInfinite(cat.getMin()) && cat.getMin() < 0) {
                    or.add("(popeve < :popeveMax" + i + ")");
                    params.addValue("popeveMax" + i, cat.getMax());
                } else if (Double.isInfinite(cat.getMax())) {
                    or.add("(popeve >= :popeveMin" + i + ")");
                    params.addValue("popeveMin" + i, cat.getMin());
                } else {
                    or.add("(popeve >= :popeveMin" + i + " AND popeve < :popeveMax" + i + ")");
                    params.addValue("popeveMin" + i, cat.getMin()).addValue("popeveMax" + i, cat.getMax());
                }
            }
            c.add("(" + String.join(" OR ", or) + ")");
        }
        if (r.getEsm1bMin() != null) {
            c.add("esm1b >= :esm1bMin");
            params.addValue("esm1bMin", r.getEsm1bMin());
        }
        if (r.getEsm1bMax() != null) {
            c.add("esm1b <= :esm1bMax");
            params.addValue("esm1bMax", r.getEsm1bMax());
        }
        if (r.getConservationMin() != null) {
            c.add("conservation >= :conservationMin");
            params.addValue("conservationMin", r.getConservationMin());
        }
        if (r.getConservationMax() != null) {
            c.add("conservation <= :conservationMax");
            params.addValue("conservationMax", r.getConservationMax());
        }
        if (notEmpty(r.getAlleleFreq())) {
            List<String> or = new ArrayList<>();
            for (int i = 0; i < r.getAlleleFreq().size(); i++) {
                AlleleFreqCategory cat = r.getAlleleFreq().get(i);
                or.add("(af >= :afMin" + i + " AND af < :afMax" + i + ")");
                params.addValue("afMin" + i, cat.getMin()).addValue("afMax" + i, cat.getMax());
            }
            c.add("(" + String.join(" OR ", or) + ")");
        }
        if (notEmpty(r.getStability())) {
            Set<StabilityChange> changes = EnumSet.copyOf(r.getStability());
            if (changes.equals(EnumSet.allOf(StabilityChange.class))) c.add("foldx_ddg_max IS NOT NULL");
            else if (changes.contains(StabilityChange.LIKELY_DESTABILISING)) c.add("foldx_ddg_max >= 2");
            else c.add("foldx_ddg_min < 2");
        }

        if (Boolean.TRUE.equals(r.getExperimentalModel())) c.add("exp_structure");
        if (Boolean.TRUE.equals(r.getPocket())) c.add("pocket");
        if (Boolean.TRUE.equals(r.getInteract())) c.add("interface");
        if (Boolean.TRUE.equals(r.getDiseaseAssociation())) c.add("disease");
        if (Boolean.TRUE.equals(r.getPtm())) c.add("ptm");
        if (Boolean.TRUE.equals(r.getMutagen())) c.add("mutagen");
        if (Boolean.TRUE.equals(r.getDomain())) c.add("domain");
        if (Boolean.TRUE.equals(r.getBinding())) c.add("binding");
        if (Boolean.TRUE.equals(r.getActsite())) c.add("actsite");
        if (Boolean.TRUE.equals(r.getTransmem())) c.add("transmem");
        return c;
    }

    private static boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }

    /** A template over the same DataSource whose statements time out after {@code seconds}. */
    private NamedParameterJdbcTemplate withTimeout(int seconds) {
        JdbcTemplate scoped = new JdbcTemplate(jdbcTemplate.getJdbcTemplate().getDataSource());
        scoped.setQueryTimeout(seconds);
        return new NamedParameterJdbcTemplate(scoped);
    }
}
