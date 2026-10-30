package uk.ac.ebi.protvar.repo;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.MappingRequest;
import uk.ac.ebi.protvar.model.response.UploadSummary;
import uk.ac.ebi.protvar.types.AlleleFreqCategory;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.CaddCategory;
import uk.ac.ebi.protvar.types.FeatureGroup;
import uk.ac.ebi.protvar.types.PopEveClass;
import uk.ac.ebi.protvar.types.StabilityChange;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Materialised user uploads for filter/sort (protvar_runtime schema, owned by the BE user).
 *
 * <p>{@code upload} holds one row per (upload id, requested assembly, data release) build;
 * {@code upload_variant} holds its rows: one per derived genomic variant × lead accession
 * per gene, tagged with the original input line number. Rows are derived from the Redis-cached input
 * and rebuilt whenever missing (Delphix refresh, clean-up, new release). Both tables are UNLOGGED so a
 * snapshot or crash empties them together — never a READY build without its rows.
 *
 * <p>Filters compile to EXISTS on each score/feature table's own key, so a variant passes on its own
 * row; a line passes if any of its rows does. Sort uses each line's best row (MAX desc / MIN asc).
 *
 * <p>With {@code app.filter.coding-variant}, a build instead copies its rows from the coding_variant table
 * (every lead at each derived variant, with all filter/sort attributes), and filters are plain column
 * conditions on upload_variant, the same ones CodingVariantRepo uses. Those builds carry a distinct release
 * key ({@link #CV_SUFFIX}), so old-format builds are never read by the new path (rebuilt on demand).
 *
 * <p>{@code upload_line} holds the note codes (MessageCode) of each line that has any, recorded while
 * building: the upload summary and the notes filter read it. A line is mapped when it has upload_variant rows.
 */
@Repository
@RequiredArgsConstructor
public class UploadVariantRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(UploadVariantRepo.class);

    public static final String BUILDING = "BUILDING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";
    /**
     * Release-key suffix of builds copied from coding_variant (fits upload.release varchar(10)). Bumped when
     * builds gain data, so older builds are rebuilt on demand: -cv, then -c2 (adds upload_line).
     */
    public static final String CV_SUFFIX = "-c2";

    // coding_variant attributes copied into upload_variant (same names and types as the table)
    private static final String CV_COLUMNS = "cadd, avi, am, am_class, popeve, esm1b, conservation, af, "
            + "foldx_ddg_max, foldx_ddg_min, known, pocket, interface, exp_structure, disease, "
            + "ptm, mutagen, domain, binding, actsite, transmem";

    private static final String DDL = """
            CREATE UNLOGGED TABLE IF NOT EXISTS protvar_runtime.upload (
              upload_key   serial PRIMARY KEY,
              upload_id    varchar(64) NOT NULL,
              assembly     varchar(10) NOT NULL,
              release      varchar(10) NOT NULL,
              status       varchar(10) NOT NULL,
              input_count  int,
              lines_done   int NOT NULL DEFAULT 0,
              row_count    int NOT NULL DEFAULT 0,
              created_at   timestamptz NOT NULL DEFAULT now(),
              last_used_at timestamptz NOT NULL DEFAULT now(),
              UNIQUE (upload_id, assembly, release)
            );
            CREATE UNLOGGED TABLE IF NOT EXISTS protvar_runtime.upload_variant (
              upload_key  int         NOT NULL,
              input_idx   int         NOT NULL,
              chromosome  varchar(2)  NOT NULL,
              position    int         NOT NULL,
              ref         varchar(1)  NOT NULL,
              alt         varchar(1)  NOT NULL,
              accession   varchar(15) NOT NULL,
              protein_pos int         NOT NULL,
              wt_aa       varchar(1),
              mt_aa       varchar(1)
            );
            CREATE INDEX IF NOT EXISTS upload_variant_key_idx_idx ON protvar_runtime.upload_variant (upload_key, input_idx);
            CREATE UNLOGGED TABLE IF NOT EXISTS protvar_runtime.upload_line (
              upload_key int    NOT NULL,
              input_idx  int    NOT NULL,
              codes      text[] NOT NULL,
              PRIMARY KEY (upload_key, input_idx)
            );
            -- coding_variant attributes (null on old-format builds)
            ALTER TABLE protvar_runtime.upload_variant
              ALTER COLUMN protein_pos DROP NOT NULL,
              ADD COLUMN IF NOT EXISTS cadd real, ADD COLUMN IF NOT EXISTS avi real,
              ADD COLUMN IF NOT EXISTS am real, ADD COLUMN IF NOT EXISTS am_class smallint,
              ADD COLUMN IF NOT EXISTS popeve real, ADD COLUMN IF NOT EXISTS esm1b real,
              ADD COLUMN IF NOT EXISTS conservation real, ADD COLUMN IF NOT EXISTS af real,
              ADD COLUMN IF NOT EXISTS foldx_ddg_max real, ADD COLUMN IF NOT EXISTS foldx_ddg_min real,
              ADD COLUMN IF NOT EXISTS known boolean, ADD COLUMN IF NOT EXISTS pocket boolean,
              ADD COLUMN IF NOT EXISTS interface boolean, ADD COLUMN IF NOT EXISTS exp_structure boolean,
              ADD COLUMN IF NOT EXISTS disease boolean, ADD COLUMN IF NOT EXISTS ptm boolean,
              ADD COLUMN IF NOT EXISTS mutagen boolean, ADD COLUMN IF NOT EXISTS domain boolean,
              ADD COLUMN IF NOT EXISTS binding boolean, ADD COLUMN IF NOT EXISTS actsite boolean,
              ADD COLUMN IF NOT EXISTS transmem boolean;
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CodingVariantRepo codingVariantRepo;

    @Value("${app.filter.coding-variant:false}") private boolean fromCodingVariant;
    @Value("${tbl.coding.variant}") private String codingVariantTable;
    @Value("${tbl.genomic_score}") private String genomicScoreTable;
    @Value("${tbl.mapping.dbsnp}") private String dbsnpTable;
    @Value("${tbl.allelefreq}") private String alleleFreqTable;
    @Value("${tbl.am}") private String amTable;
    @Value("${tbl.popeve}") private String popeveTable;
    @Value("${tbl.uprefseq}") private String uniprotRefseqTable;
    @Value("${tbl.esm}") private String esmTable;
    @Value("${tbl.conserv}") private String conservationTable;
    @Value("${tbl.ann.str}") private String structureTable;
    @Value("${tbl.pocket.v2}") private String pocketTable;
    @Value("${tbl.interfaces}") private String interfacesTable;
    @Value("${tbl.foldx}") private String foldxTable;
    @Value("${tbl.ann.pop}") private String populationTable;
    @Value("${tbl.ann.fun.feature}") private String functionFeatureTable;

    public record Upload(int key, String status, Integer inputCount, int linesDone, boolean stale) {}

    /** One upload_variant row; mtAa/wtAa may be null (non-codable codon). */
    public record Row(int inputIdx, String chromosome, int position, String ref, String alt,
                      String accession, int proteinPos, String wtAa, String mtAa) {}

    /** A row that passed the filter — enough to trim the displayed mapping. */
    public record Passing(int inputIdx, String chromosome, int position, String alt, String accession) {}

    @PostConstruct
    void ensureSchema() {
        // the schema is owned by the BE user on prod; Delphix copies carry it after their next refresh
        try {
            jdbcTemplate.getJdbcTemplate().execute(DDL);
        } catch (Exception e) {
            LOGGER.warn("protvar_runtime tables unavailable; upload filter/sort disabled until they exist: {}",
                    e.getMessage());
        }
    }

    // ---------------------------------------------------------------- build bookkeeping

    public Optional<Upload> find(String uploadId, String assembly, String release, int staleMinutes) {
        String sql = """
                SELECT upload_key, status, input_count, lines_done,
                       last_used_at < now() - make_interval(mins => :stale) AS stale
                FROM protvar_runtime.upload
                WHERE upload_id = :id AND assembly = :asm AND release = :rel
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", uploadId).addValue("asm", assembly).addValue("rel", release)
                .addValue("stale", staleMinutes);
        return jdbcTemplate.query(sql, params, (rs, i) -> new Upload(rs.getInt("upload_key"),
                        rs.getString("status"), (Integer) rs.getObject("input_count"),
                        rs.getInt("lines_done"), rs.getBoolean("stale")))
                .stream().findFirst();
    }

    /**
     * Claims the build for (upload, assembly, release): inserts a BUILDING row, or takes over a FAILED one
     * or a BUILDING one whose heartbeat went stale (pod died). The unique key makes this the lock across
     * BE pods. Returns the key when this caller owns the build, empty when someone else does.
     */
    public Optional<Integer> claim(String uploadId, String assembly, String release, int inputCount,
                                   int staleMinutes) {
        String sql = """
                INSERT INTO protvar_runtime.upload (upload_id, assembly, release, status, input_count)
                VALUES (:id, :asm, :rel, 'BUILDING', :n)
                ON CONFLICT (upload_id, assembly, release) DO UPDATE
                  SET status = 'BUILDING', input_count = :n, lines_done = 0, row_count = 0, last_used_at = now()
                  WHERE upload.status = 'FAILED'
                     OR (upload.status = 'BUILDING' AND upload.last_used_at < now() - make_interval(mins => :stale))
                RETURNING upload_key
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", uploadId).addValue("asm", assembly).addValue("rel", release)
                .addValue("n", inputCount).addValue("stale", staleMinutes);
        Optional<Integer> key = jdbcTemplate.queryForList(sql, params, Integer.class).stream().findFirst();
        // a taken-over build may have left partial rows
        key.ifPresent(this::deleteRows);
        return key;
    }

    /** Records build progress; also the heartbeat that keeps another pod from taking the build over. */
    public void progress(int key, int linesDone) {
        jdbcTemplate.update("UPDATE protvar_runtime.upload SET lines_done = :n, last_used_at = now() WHERE upload_key = :k",
                new MapSqlParameterSource("k", key).addValue("n", linesDone));
    }

    public void finish(int key, int rowCount) {
        if (rowCount > 50_000) {
            // fresh bulk load: give the planner real stats before the first filter query
            jdbcTemplate.getJdbcTemplate().execute("ANALYZE protvar_runtime.upload_variant");
        }
        jdbcTemplate.update("""
                UPDATE protvar_runtime.upload SET status = 'READY', lines_done = input_count, row_count = :n, last_used_at = now()
                WHERE upload_key = :k""", new MapSqlParameterSource("k", key).addValue("n", rowCount));
    }

    public void fail(int key) {
        jdbcTemplate.update("UPDATE protvar_runtime.upload SET status = 'FAILED' WHERE upload_key = :k",
                new MapSqlParameterSource("k", key));
    }

    public void touch(int key) {
        jdbcTemplate.update("UPDATE protvar_runtime.upload SET last_used_at = now() WHERE upload_key = :k",
                new MapSqlParameterSource("k", key));
    }

    private void deleteRows(int key) {
        jdbcTemplate.update("DELETE FROM protvar_runtime.upload_variant WHERE upload_key = :k",
                new MapSqlParameterSource("k", key));
        jdbcTemplate.update("DELETE FROM protvar_runtime.upload_line WHERE upload_key = :k",
                new MapSqlParameterSource("k", key));
    }

    /** Bulk-inserts one chunk of rows as parallel arrays (one round trip). */
    public void insert(int key, List<Row> rows) {
        if (rows.isEmpty()) return;
        String sql = """
                INSERT INTO protvar_runtime.upload_variant
                  (upload_key, input_idx, chromosome, position, ref, alt, accession, protein_pos, wt_aa, mt_aa)
                SELECT :k, * FROM unnest(:idx::int[], :chr::varchar[], :pos::int[], :ref::varchar[], :alt::varchar[],
                                         :acc::varchar[], :ppos::int[], :wt::varchar[], :mt::varchar[])
                """;
        var params = new MapSqlParameterSource("k", key)
                .addValue("idx", rows.stream().map(Row::inputIdx).toArray(Integer[]::new))
                .addValue("chr", rows.stream().map(Row::chromosome).toArray(String[]::new))
                .addValue("pos", rows.stream().map(Row::position).toArray(Integer[]::new))
                .addValue("ref", rows.stream().map(Row::ref).toArray(String[]::new))
                .addValue("alt", rows.stream().map(Row::alt).toArray(String[]::new))
                .addValue("acc", rows.stream().map(Row::accession).toArray(String[]::new))
                .addValue("ppos", rows.stream().map(Row::proteinPos).toArray(Integer[]::new))
                .addValue("wt", rows.stream().map(Row::wtAa).toArray(String[]::new))
                .addValue("mt", rows.stream().map(Row::mtAa).toArray(String[]::new));
        jdbcTemplate.update(sql, params);
    }

    /** Records the note codes of one chunk's lines (line number → codes); lines without notes are left out. */
    public void insertLines(int key, Map<Integer, List<String>> codesByLine) {
        if (codesByLine.isEmpty()) return;
        String sql = """
                INSERT INTO protvar_runtime.upload_line (upload_key, input_idx, codes)
                SELECT :k, idx, string_to_array(codes, ',') FROM unnest(:idx::int[], :codes::text[]) AS u(idx, codes)
                """;
        var params = new MapSqlParameterSource("k", key)
                .addValue("idx", codesByLine.keySet().toArray(Integer[]::new))
                .addValue("codes", codesByLine.values().stream().map(c -> String.join(",", c)).toArray(String[]::new));
        jdbcTemplate.update(sql, params);
    }

    /** Lines mapped (with rows) and with notes, and lines per note code, of a prepared build. */
    public UploadSummary summary(int key) {
        var params = new MapSqlParameterSource("k", key);
        Map<String, Long> codes = new TreeMap<>();
        jdbcTemplate.query("""
                SELECT code, count(*) AS n FROM protvar_runtime.upload_line, unnest(codes) AS code
                WHERE upload_key = :k GROUP BY code""", params,
                rs -> { codes.put(rs.getString("code"), rs.getLong("n")); });
        return jdbcTemplate.queryForObject("""
                SELECT u.input_count,
                       (SELECT count(DISTINCT input_idx) FROM protvar_runtime.upload_variant WHERE upload_key = :k) AS mapped,
                       (SELECT count(*) FROM protvar_runtime.upload_line WHERE upload_key = :k) AS with_notes
                FROM protvar_runtime.upload u WHERE u.upload_key = :k""", params,
                (rs, i) -> UploadSummary.prepared(rs.getLong("input_count"), rs.getLong("mapped"),
                        rs.getLong("with_notes"), codes));
    }

    /** Whether builds copy their rows from coding_variant (see the class comment). */
    public boolean fromCodingVariant() {
        return fromCodingVariant;
    }

    /**
     * Inserts one chunk's derived variants with every coding_variant row at each: one per gene lead, with
     * its attributes. Only the line number and (chromosome, position, ref, alt) of each Row are used; a
     * variant not in the table (unmapped, or no reference base) adds no rows. Returns the rows inserted.
     */
    public int insertFromCodingVariant(int key, List<Row> rows) {
        if (rows.isEmpty()) return 0;
        // genomic order: neighbouring lookups reuse the same index and table pages (the table is in that order)
        List<Row> variants = rows.stream()
                .sorted(Comparator.comparing(Row::chromosome).thenComparingInt(Row::position)).toList();
        String sql = """
                INSERT INTO protvar_runtime.upload_variant
                  (upload_key, input_idx, chromosome, position, ref, alt, accession, protein_pos, wt_aa, mt_aa, %1$s)
                SELECT :k, u.idx, cv.chromosome, cv.position, cv.ref, cv.alt, cv.accession, cv.protein_pos,
                       cv.wt_aa, cv.mt_aa, %2$s
                FROM unnest(:idx::int[], :chr::varchar[], :pos::int[], :ref::varchar[], :alt::varchar[])
                       AS u(idx, chr, pos, ref, alt)
                JOIN %3$s cv ON cv.chromosome = u.chr AND cv.position = u.pos AND cv.ref = u.ref AND cv.alt = u.alt
                """.formatted(CV_COLUMNS, "cv." + CV_COLUMNS.replace(", ", ", cv."), codingVariantTable);
        var params = new MapSqlParameterSource("k", key)
                .addValue("idx", variants.stream().map(Row::inputIdx).toArray(Integer[]::new))
                .addValue("chr", variants.stream().map(Row::chromosome).toArray(String[]::new))
                .addValue("pos", variants.stream().map(Row::position).toArray(Integer[]::new))
                .addValue("ref", variants.stream().map(Row::ref).toArray(String[]::new))
                .addValue("alt", variants.stream().map(Row::alt).toArray(String[]::new));
        return jdbcTemplate.update(sql, params);
    }

    /** Drops builds unused for {@code days}, and builds of other releases. Returns builds removed. */
    public int deleteExpired(int days, String currentRelease) {
        var params = new MapSqlParameterSource("days", days).addValue("rel", currentRelease);
        String expired = """
                SELECT upload_key FROM protvar_runtime.upload
                WHERE last_used_at < now() - make_interval(days => :days) OR release <> :rel""";
        jdbcTemplate.update("DELETE FROM protvar_runtime.upload_variant WHERE upload_key IN (" + expired + ")", params);
        jdbcTemplate.update("DELETE FROM protvar_runtime.upload_line WHERE upload_key IN (" + expired + ")", params);
        return jdbcTemplate.update("DELETE FROM protvar_runtime.upload WHERE upload_key IN (" + expired + ")", params);
    }

    // ---------------------------------------------------------------- filter / sort

    /**
     * Number of matching input lines: lines with at least one passing row, or, with only a notes filter, any
     * line (mapped or not) whose notes match.
     */
    public long countLines(int key, MappingRequest request) {
        var params = new MapSqlParameterSource("k", key);
        String sql = rowFiltersOrSorts(request)
                ? "SELECT count(DISTINCT uv.input_idx) FROM protvar_runtime.upload_variant uv\n"
                    + where(request, params) + lineConditions(request, params, "uv.input_idx")
                : "SELECT count(*) FROM " + ALL_LINES + "\nWHERE true" + lineConditions(request, params, "l.idx");
        return withFilterTimeout().queryForObject(sql, params, Long.class);
    }

    // every line number of the build, for a notes filter that must also find lines without rows
    private static final String ALL_LINES = "generate_series(0, "
            + "(SELECT input_count FROM protvar_runtime.upload WHERE upload_key = :k) - 1) AS l(idx)";

    /** One page of passing input line numbers, in sort order (input order when unsorted). */
    public List<Integer> pageLines(int key, MappingRequest request, long offset, int limit) {
        var params = new MapSqlParameterSource("k", key).addValue("limit", limit).addValue("offset", offset);
        String sortExpr = sortExpr(request);
        String sql;
        if (!rowFiltersOrSorts(request)) {
            sql = "SELECT l.idx FROM " + ALL_LINES + "\nWHERE true" + lineConditions(request, params, "l.idx")
                    + "\nORDER BY l.idx LIMIT :limit OFFSET :offset";
        } else if (sortExpr == null) {
            sql = "SELECT DISTINCT uv.input_idx FROM protvar_runtime.upload_variant uv\n"
                    + where(request, params) + lineConditions(request, params, "uv.input_idx")
                    + "\nORDER BY uv.input_idx LIMIT :limit OFFSET :offset";
        } else {
            boolean asc = "asc".equalsIgnoreCase(request.getOrder());
            sql = "WITH r AS (SELECT uv.input_idx, " + sortExpr + " AS s FROM protvar_runtime.upload_variant uv\n"
                    + where(request, params) + lineConditions(request, params, "uv.input_idx") + ")\n"
                    + "SELECT input_idx FROM r GROUP BY input_idx\n"
                    + "ORDER BY " + (asc ? "min(s) ASC" : "max(s) DESC") + " NULLS LAST, input_idx\n"
                    + "LIMIT :limit OFFSET :offset";
        }
        return withFilterTimeout().queryForList(sql, params, Integer.class);
    }

    /** The passing rows of the given lines. */
    public List<Passing> passingRows(int key, MappingRequest request, List<Integer> lines) {
        if (lines.isEmpty()) return List.of();
        var params = new MapSqlParameterSource("k", key).addValue("lines", lines.toArray(Integer[]::new));
        String sql = "SELECT uv.input_idx, uv.chromosome, uv.position, uv.alt, uv.accession\n"
                + "FROM protvar_runtime.upload_variant uv\n"
                + where(request, params) + "\nAND uv.input_idx = ANY(:lines)";
        return withFilterTimeout().query(sql, params, (rs, i) -> new Passing(rs.getInt("input_idx"),
                rs.getString("chromosome"), rs.getInt("position"), rs.getString("alt"), rs.getString("accession")));
    }

    /**
     * Filter queries get a time budget (as browse's page query does) so a slow one fails with a clear message
     * instead of running past the gateway's ~60 s and surfacing as a 500.
     */
    public static final int FILTER_TIMEOUT_SECONDS = 20;

    private NamedParameterJdbcTemplate withFilterTimeout() {
        org.springframework.jdbc.core.JdbcTemplate scoped =
                new org.springframework.jdbc.core.JdbcTemplate(jdbcTemplate.getJdbcTemplate().getDataSource());
        scoped.setQueryTimeout(FILTER_TIMEOUT_SECONDS);
        return new NamedParameterJdbcTemplate(scoped);
    }

    /** True when the request filters or sorts (i.e. needs the materialised upload). */
    public static boolean filtersOrSorts(MappingRequest r) {
        return rowFiltersOrSorts(r) || notesFilter(r);
    }

    /** True when the request filters on the lines' notes (notes=any|none, codes=PV-…). */
    public static boolean notesFilter(MappingRequest r) {
        return "any".equalsIgnoreCase(r.getNotes()) || "none".equalsIgnoreCase(r.getNotes()) || notEmpty(r.getCodes());
    }

    /**
     * Notes conditions on a line number ({@code idx}): " AND …" for each active notes filter, or "".
     * A line's notes are its upload_line row (no row = no notes).
     */
    String lineConditions(MappingRequest request, MapSqlParameterSource params, String idx) {
        String line = "SELECT 1 FROM protvar_runtime.upload_line ul WHERE ul.upload_key = :k AND ul.input_idx = " + idx;
        StringBuilder c = new StringBuilder();
        if ("any".equalsIgnoreCase(request.getNotes())) c.append("\n  AND EXISTS (").append(line).append(")");
        if ("none".equalsIgnoreCase(request.getNotes())) c.append("\n  AND NOT EXISTS (").append(line).append(")");
        if (notEmpty(request.getCodes())) {
            params.addValue("codes", request.getCodes().toArray(String[]::new));
            c.append("\n  AND EXISTS (").append(line).append(" AND ul.codes && :codes::text[])");
        }
        return c.toString();
    }

    /** True when the request filters or sorts on the lines' rows (scores, features, …), not only on notes. */
    private static boolean rowFiltersOrSorts(MappingRequest r) {
        return Boolean.TRUE.equals(r.getKnown())
                || notEmpty(r.getCadd()) || r.getAviMin() != null
                || notEmpty(r.getAm()) || notEmpty(r.getPopeve())
                || r.getEsm1bMin() != null || r.getEsm1bMax() != null
                || r.getConservationMin() != null || r.getConservationMax() != null
                || notEmpty(r.getAlleleFreq()) || notEmpty(r.getStability())
                || Boolean.TRUE.equals(r.getExperimentalModel()) || Boolean.TRUE.equals(r.getPocket())
                || Boolean.TRUE.equals(r.getInteract()) || Boolean.TRUE.equals(r.getDiseaseAssociation())
                || Boolean.TRUE.equals(r.getPtm()) || Boolean.TRUE.equals(r.getMutagen())
                || Boolean.TRUE.equals(r.getDomain()) || Boolean.TRUE.equals(r.getBinding())
                || Boolean.TRUE.equals(r.getActsite()) || Boolean.TRUE.equals(r.getTransmem())
                || sortExprKey(r) != null;
    }

    private static boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }

    private static String sortExprKey(MappingRequest r) {
        String s = r.getSort();
        if (s == null) return null;
        s = s.toLowerCase();
        return Set.of("cadd", "avi", "am", "popeve", "esm1b").contains(s) ? s : null;
    }

    private String sortExpr(MappingRequest request) {
        String key = sortExprKey(request);
        if (key == null) return null;
        if (fromCodingVariant) return "uv." + key;   // copied column, same name as the sort key
        return switch (key) {
            case "cadd", "avi" -> """
                    (SELECT max(gs.%s) FROM %s gs WHERE gs.chromosome = uv.chromosome AND gs.position = uv.position
                       AND gs.reference_allele = uv.ref AND gs.alt_allele = uv.alt)"""
                    .formatted(key.equals("cadd") ? "score" : "avi_score", genomicScoreTable);
            case "am" -> """
                    (SELECT max(am.am_pathogenicity) FROM %s am WHERE am.accession = uv.accession
                       AND am.position = uv.protein_pos AND am.wt_aa = uv.wt_aa AND am.mt_aa = uv.mt_aa)"""
                    .formatted(amTable);
            case "popeve" -> """
                    (SELECT max(p.popeve) FROM %s r JOIN %s p ON p.refseq_protein = r.refseq_acc
                     WHERE r.uniprot_acc = uv.accession AND p.position = uv.protein_pos
                       AND p.wt_aa = uv.wt_aa AND p.mt_aa = uv.mt_aa)"""
                    .formatted(uniprotRefseqTable, popeveTable);
            default -> """
                    (SELECT max(esm.score) FROM %s esm WHERE esm.accession = uv.accession
                       AND esm.position = uv.protein_pos AND esm.mt_aa = uv.mt_aa)"""
                    .formatted(esmTable);
        };
    }

    /**
     * WHERE clause: the upload key plus, per active filter (AND across filters), a column condition on the
     * copied coding_variant attributes, or an EXISTS on the source table for old-format builds.
     */
    String where(MappingRequest request, MapSqlParameterSource params) {
        List<String> c = new ArrayList<>();
        c.add("uv.upload_key = :k");
        if (fromCodingVariant) {
            c.addAll(codingVariantRepo.filters(request, params));   // unqualified: upload_variant is the only table
            return "WHERE " + String.join("\n  AND ", c);
        }

        if (Boolean.TRUE.equals(request.getKnown())) {
            c.add("""
                    EXISTS (SELECT 1 FROM %s d WHERE d.chr = uv.chromosome AND d.pos = uv.position
                              AND d.ref = uv.ref AND uv.alt = ANY(d.known_alts))""".formatted(dbsnpTable));
        }

        List<String> gs = new ArrayList<>();
        if (notEmpty(request.getCadd())) {
            List<String> or = new ArrayList<>();
            for (int i = 0; i < request.getCadd().size(); i++) {
                CaddCategory cat = request.getCadd().get(i);
                or.add("(gs.score >= :caddMin" + i + " AND gs.score < :caddMax" + i + ")");
                params.addValue("caddMin" + i, cat.getMin()).addValue("caddMax" + i, cat.getMax());
            }
            gs.add("(" + String.join(" OR ", or) + ")");
        }
        if (request.getAviMin() != null) {
            gs.add("gs.avi_score >= :aviMin");
            params.addValue("aviMin", request.getAviMin());
        }
        if (!gs.isEmpty()) {
            c.add("""
                    EXISTS (SELECT 1 FROM %s gs WHERE gs.chromosome = uv.chromosome AND gs.position = uv.position
                              AND gs.reference_allele = uv.ref AND gs.alt_allele = uv.alt AND %s)"""
                    .formatted(genomicScoreTable, String.join(" AND ", gs)));
        }

        if (notEmpty(request.getAlleleFreq())) {
            List<String> or = new ArrayList<>();
            for (int i = 0; i < request.getAlleleFreq().size(); i++) {
                AlleleFreqCategory cat = request.getAlleleFreq().get(i);
                or.add("(af.af >= :afMin" + i + " AND af.af < :afMax" + i + ")");
                params.addValue("afMin" + i, cat.getMin()).addValue("afMax" + i, cat.getMax());
            }
            c.add("""
                    EXISTS (SELECT 1 FROM %s af WHERE af.chr = uv.chromosome AND af.pos = uv.position
                              AND af.ref = uv.ref AND af.alt = uv.alt AND (%s))"""
                    .formatted(alleleFreqTable, String.join(" OR ", or)));
        }

        if (notEmpty(request.getAm())) {
            params.addValue("amClasses", request.getAm().stream().map(AmClass::getValue).toArray(Integer[]::new));
            c.add("""
                    EXISTS (SELECT 1 FROM %s am WHERE am.accession = uv.accession AND am.position = uv.protein_pos
                              AND am.wt_aa = uv.wt_aa AND am.mt_aa = uv.mt_aa AND am.am_class = ANY(:amClasses))"""
                    .formatted(amTable));
        }

        if (notEmpty(request.getPopeve())) {
            List<String> or = new ArrayList<>();
            for (int i = 0; i < request.getPopeve().size(); i++) {
                PopEveClass cat = request.getPopeve().get(i);
                if (Double.isInfinite(cat.getMin()) && cat.getMin() < 0) {
                    or.add("(p.popeve < :popeveMax" + i + ")");
                    params.addValue("popeveMax" + i, cat.getMax());
                } else if (Double.isInfinite(cat.getMax())) {
                    or.add("(p.popeve >= :popeveMin" + i + ")");
                    params.addValue("popeveMin" + i, cat.getMin());
                } else {
                    or.add("(p.popeve >= :popeveMin" + i + " AND p.popeve < :popeveMax" + i + ")");
                    params.addValue("popeveMin" + i, cat.getMin()).addValue("popeveMax" + i, cat.getMax());
                }
            }
            c.add("""
                    EXISTS (SELECT 1 FROM %s r JOIN %s p ON p.refseq_protein = r.refseq_acc
                            WHERE r.uniprot_acc = uv.accession AND p.position = uv.protein_pos
                              AND p.wt_aa = uv.wt_aa AND p.mt_aa = uv.mt_aa AND (%s))"""
                    .formatted(uniprotRefseqTable, popeveTable, String.join(" OR ", or)));
        }

        if (request.getEsm1bMin() != null || request.getEsm1bMax() != null) {
            List<String> and = new ArrayList<>();
            if (request.getEsm1bMin() != null) {
                and.add("esm.score >= :esm1bMin");
                params.addValue("esm1bMin", request.getEsm1bMin());
            }
            if (request.getEsm1bMax() != null) {
                and.add("esm.score <= :esm1bMax");
                params.addValue("esm1bMax", request.getEsm1bMax());
            }
            c.add("""
                    EXISTS (SELECT 1 FROM %s esm WHERE esm.accession = uv.accession AND esm.position = uv.protein_pos
                              AND esm.mt_aa = uv.mt_aa AND %s)""".formatted(esmTable, String.join(" AND ", and)));
        }

        if (request.getConservationMin() != null || request.getConservationMax() != null) {
            List<String> and = new ArrayList<>();
            if (request.getConservationMin() != null) {
                and.add("cons.score >= :conservationMin");
                params.addValue("conservationMin", request.getConservationMin());
            }
            if (request.getConservationMax() != null) {
                and.add("cons.score <= :conservationMax");
                params.addValue("conservationMax", request.getConservationMax());
            }
            c.add("""
                    EXISTS (SELECT 1 FROM %s cons WHERE cons.accession = uv.accession AND cons.position = uv.protein_pos
                              AND cons.aa = uv.wt_aa AND %s)""".formatted(conservationTable, String.join(" AND ", and)));
        }

        if (notEmpty(request.getStability())) {
            String ddg = "";
            Set<StabilityChange> changes = EnumSet.copyOf(request.getStability());
            if (!changes.equals(EnumSet.allOf(StabilityChange.class))) {
                if (changes.contains(StabilityChange.LIKELY_DESTABILISING)) ddg = " AND f.foldx_ddg >= 2";
                else if (changes.contains(StabilityChange.UNLIKELY_DESTABILISING)) ddg = " AND f.foldx_ddg < 2";
            }
            c.add("""
                    EXISTS (SELECT 1 FROM %s f WHERE f.protein_acc = uv.accession AND f.position = uv.protein_pos
                              AND f.mutated_type = uv.mt_aa%s)""".formatted(foldxTable, ddg));
        }

        if (Boolean.TRUE.equals(request.getExperimentalModel())) {
            c.add("""
                    EXISTS (SELECT 1 FROM %s s WHERE s.accession = uv.accession
                              AND uv.protein_pos BETWEEN s.unp_start AND s.unp_end)""".formatted(structureTable));
        }
        if (Boolean.TRUE.equals(request.getPocket())) {
            c.add("""
                    EXISTS (SELECT 1 FROM %s p WHERE p.struct_id = uv.accession
                              AND uv.protein_pos = ANY(p.pocket_resid))""".formatted(pocketTable));
        }
        if (Boolean.TRUE.equals(request.getInteract())) {
            c.add("""
                    (EXISTS (SELECT 1 FROM %1$s i WHERE i.a = uv.accession AND uv.protein_pos = ANY(i.a_residues))
                     OR EXISTS (SELECT 1 FROM %1$s i WHERE i.b = uv.accession AND uv.protein_pos = ANY(i.b_residues)))"""
                    .formatted(interfacesTable));
        }
        if (Boolean.TRUE.equals(request.getDiseaseAssociation())) {
            c.add("""
                    EXISTS (SELECT 1 FROM %s pop WHERE pop.accession = uv.accession AND pop.position = uv.protein_pos
                              AND pop.disease IS TRUE)""".formatted(populationTable));
        }

        // function-side feature groups: AND across groups, OR within a group (as MappingRepo)
        Map<String, FeatureGroup> groups = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(request.getPtm()))      groups.put("ptmTypes", FeatureGroup.PTM);
        if (Boolean.TRUE.equals(request.getMutagen()))  groups.put("mutagenTypes", FeatureGroup.MUTAGENESIS);
        if (Boolean.TRUE.equals(request.getDomain()))   groups.put("domainTypes", FeatureGroup.DOMAIN);
        if (Boolean.TRUE.equals(request.getBinding()))  groups.put("bindingTypes", FeatureGroup.BINDING_SITE);
        if (Boolean.TRUE.equals(request.getActsite()))  groups.put("actsiteTypes", FeatureGroup.ACTIVE_SITE);
        if (Boolean.TRUE.equals(request.getTransmem())) groups.put("transmemTypes", FeatureGroup.TRANSMEMBRANE);
        for (Map.Entry<String, FeatureGroup> e : groups.entrySet()) {
            params.addValue(e.getKey(), e.getValue().getFeatureTypeNames());
            c.add("""
                    EXISTS (SELECT 1 FROM %s ff WHERE ff.accession = uv.accession AND ff.type IN (:%s)
                              AND ((ff.type <> 'DISULFID' AND uv.protein_pos BETWEEN ff.begin_pos AND ff.end_pos)
                                OR (ff.type =  'DISULFID' AND uv.protein_pos IN (ff.begin_pos, ff.end_pos))))"""
                    .formatted(functionFeatureTable, e.getKey()));
        }

        return "WHERE " + String.join("\n  AND ", c);
    }
}
