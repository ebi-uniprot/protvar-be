package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.ebi.protvar.model.score.*;
import uk.ac.ebi.protvar.types.AmClass;
import uk.ac.ebi.protvar.types.EveClass;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ScoreNewRepo {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    // Table names from configuration
    @Value("${tbl.conserv}")
    private String conservTableName;

    @Value("${tbl.eve}")
    private String eveTableName;

    @Value("${tbl.esm}")
    private String esmTableName;

    @Value("${tbl.am}")
    private String amTableName;

    @Value("${tbl.popeve}")
    private String popeveTableName;

    @Value("${tbl.uprefseq}")
    private String uniprotRefseqTableName;

    // ============================================================================
    // SINGLE VARIANT QUERIES (acc, pos, mt)
    // ============================================================================

    private Optional<ConservScore> getConservScore(String acc, Integer pos) {
        String sql = String.format("""
            SELECT accession, position, score
            FROM %s
            WHERE accession = :acc AND position = :pos
            """, conservTableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("acc", acc)
                .addValue("pos", pos);

        List<ConservScore> results = jdbcTemplate.query(sql, params, conservScoreMapper);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private List<EveScore> getEveScores(String acc, Integer pos, String mt) {
        StringBuilder sql = new StringBuilder(String.format("""
            SELECT accession, position, mt_aa, score, class
            FROM %s
            WHERE accession = :acc AND position = :pos
            """, eveTableName));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("acc", acc)
                .addValue("pos", pos);

        if (mt != null && !mt.isBlank()) {
            sql.append(" AND mt_aa = :mt");
            params.addValue("mt", mt);
        }

        return jdbcTemplate.query(sql.toString(), params, eveScoreMapper);
    }

    private List<EsmScore> getEsmScores(String acc, Integer pos, String mt) {
        StringBuilder sql = new StringBuilder(String.format("""
            SELECT accession, position, mt_aa, score
            FROM %s
            WHERE accession = :acc AND position = :pos
            """, esmTableName));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("acc", acc)
                .addValue("pos", pos);

        if (mt != null && !mt.isBlank()) {
            sql.append(" AND mt_aa = :mt");
            params.addValue("mt", mt);
        }

        return jdbcTemplate.query(sql.toString(), params, esmScoreMapper);
    }

    private List<AmScore> getAmScores(String acc, Integer pos, String mt) {
        StringBuilder sql = new StringBuilder(String.format("""
            SELECT accession, position, mt_aa, am_pathogenicity, am_class
            FROM %s
            WHERE accession = :acc AND position = :pos
            """, amTableName));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("acc", acc)
                .addValue("pos", pos);

        if (mt != null && !mt.isBlank()) {
            sql.append(" AND mt_aa = :mt");
            params.addValue("mt", mt);
        }

        return jdbcTemplate.query(sql.toString(), params, amScoreMapper);
    }

    private List<PopEveScore> getPopEveScores(String acc, Integer pos, String mt) {
        StringBuilder sql = new StringBuilder(String.format("""
            SELECT ur.uniprot_acc AS accession,
                   p.position,
                   p.wt_aa,
                   p.mt_aa,
                   p.gap_freq,
                   p.popeve,
                   p.popped_eve,
                   p.popped_esm_1v,
                   p.eve,
                   p.esm_1v
            FROM %s p
            JOIN %s ur ON p.refseq_protein = ur.refseq_acc
            WHERE ur.uniprot_acc = :acc AND p.position = :pos
            """, popeveTableName, uniprotRefseqTableName));

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("acc", acc)
                .addValue("pos", pos);

        if (mt != null && !mt.isBlank()) {
            sql.append(" AND p.mt_aa = :mt");
            params.addValue("mt", mt);
        }

        return jdbcTemplate.query(sql.toString(), params, popEveScoreMapper);
    }

    // ============================================================================
    // BATCH QUERIES (multiple acc/pos pairs)
    // ============================================================================

    private List<ConservScore> getConservScoresBatch(String[] accs, Integer[] positions) {
        String sql = String.format("""
            WITH input(acc, pos) AS (
                SELECT * FROM unnest(:accs::VARCHAR[], :positions::INT[])
            )
            SELECT s.accession, s.position, s.score
            FROM %s s
            JOIN input i ON s.accession = i.acc AND s.position = i.pos
            """, conservTableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accs", accs)
                .addValue("positions", positions);

        return jdbcTemplate.query(sql, params, conservScoreMapper);
    }

    private List<EveScore> getEveScoresBatch(String[] accs, Integer[] positions) {
        String sql = String.format("""
            WITH input(acc, pos) AS (
                SELECT * FROM unnest(:accs::VARCHAR[], :positions::INT[])
            )
            SELECT s.accession, s.position, s.mt_aa, s.score, s.class
            FROM %s s
            JOIN input i ON s.accession = i.acc AND s.position = i.pos
            """, eveTableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accs", accs)
                .addValue("positions", positions);

        return jdbcTemplate.query(sql, params, eveScoreMapper);
    }

    private List<EsmScore> getEsmScoresBatch(String[] accs, Integer[] positions) {
        String sql = String.format("""
            WITH input(acc, pos) AS (
                SELECT * FROM unnest(:accs::VARCHAR[], :positions::INT[])
            )
            SELECT s.accession, s.position, s.mt_aa, s.score
            FROM %s s
            JOIN input i ON s.accession = i.acc AND s.position = i.pos
            """, esmTableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accs", accs)
                .addValue("positions", positions);

        return jdbcTemplate.query(sql, params, esmScoreMapper);
    }

    private List<AmScore> getAmScoresBatch(String[] accs, Integer[] positions) {
        String sql = String.format("""
            WITH input(acc, pos) AS (
                SELECT * FROM unnest(:accs::VARCHAR[], :positions::INT[])
            )
            SELECT s.accession, s.position, s.mt_aa, s.am_pathogenicity, s.am_class
            FROM %s s
            JOIN input i ON s.accession = i.acc AND s.position = i.pos
            """, amTableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accs", accs)
                .addValue("positions", positions);

        return jdbcTemplate.query(sql, params, amScoreMapper);
    }

    private List<PopEveScore> getPopEveScoresBatch(String[] accs, Integer[] positions) {
        String sql = String.format("""
            WITH input(acc, pos) AS (
                SELECT * FROM unnest(:accs::VARCHAR[], :positions::INT[])
            )
            SELECT ur.uniprot_acc AS accession,
                   p.position,
                   p.wt_aa,
                   p.mt_aa,
                   p.gap_freq,
                   p.popeve,
                   p.popped_eve,
                   p.popped_esm_1v,
                   p.eve,
                   p.esm_1v
            FROM %s p
            JOIN %s ur ON p.refseq_protein = ur.refseq_acc
            JOIN input i ON ur.uniprot_acc = i.acc AND p.position = i.pos
            """, popeveTableName, uniprotRefseqTableName);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("accs", accs)
                .addValue("positions", positions);

        return jdbcTemplate.query(sql, params, popEveScoreMapper);
    }

    // ============================================================================
    // BY ACCESSION QUERIES (all positions for one accession)
    // ============================================================================

    private List<ConservScore> getConservScoresByAccession(String acc) {
        String sql = String.format(
                "SELECT accession, position, score FROM %s WHERE accession = :acc",
                conservTableName);
        return jdbcTemplate.query(sql, Map.of("acc", acc), conservScoreMapper);
    }

    private List<EveScore> getEveScoresByAccession(String acc) {
        String sql = String.format(
                "SELECT accession, position, mt_aa, score, class FROM %s WHERE accession = :acc",
                eveTableName);
        return jdbcTemplate.query(sql, Map.of("acc", acc), eveScoreMapper);
    }

    private List<EsmScore> getEsmScoresByAccession(String acc) {
        String sql = String.format(
                "SELECT accession, position, mt_aa, score FROM %s WHERE accession = :acc",
                esmTableName);
        return jdbcTemplate.query(sql, Map.of("acc", acc), esmScoreMapper);
    }

    private List<AmScore> getAmScoresByAccession(String acc) {
        String sql = String.format(
                "SELECT accession, position, mt_aa, am_pathogenicity, am_class FROM %s WHERE accession = :acc",
                amTableName);
        return jdbcTemplate.query(sql, Map.of("acc", acc), amScoreMapper);
    }

    private List<PopEveScore> getPopEveScoresByAccession(String acc) {
        String sql = String.format("""
            SELECT ur.uniprot_acc AS accession,
                   p.position,
                   p.wt_aa,
                   p.mt_aa,
                   p.gap_freq,
                   p.popeve,
                   p.popped_eve,
                   p.popped_esm_1v,
                   p.eve,
                   p.esm_1v
            FROM %s p
            JOIN %s ur ON p.refseq_protein = ur.refseq_acc
            WHERE ur.uniprot_acc = :acc
            """, popeveTableName, uniprotRefseqTableName);
        return jdbcTemplate.query(sql, Map.of("acc", acc), popEveScoreMapper);
    }

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    /**
     * Get scores for a single variant (acc, pos, optionally mt)
     * @Transactional ensures all queries use the same connection
     *
     * Used in ScoreController.getScores()
     */
    @Transactional(readOnly = true)
    public List<Score> getScores(String acc, Integer pos, String mt, ScoreType type) {
        if (acc == null || pos == null) {
            return Collections.emptyList();
        }

        // If specific type requested, query only that type
        if (type != null) {
            return switch (type) {
                case CONSERV -> getConservScore(acc, pos)
                        .map(s -> (Score) s.copySubclassFields())
                        .stream().toList();
                case EVE -> getEveScores(acc, pos, mt).stream()
                        .map(Score::copySubclassFields)
                        .toList();
                case ESM -> getEsmScores(acc, pos, mt).stream()
                        .map(Score::copySubclassFields)
                        .toList();
                case AM -> getAmScores(acc, pos, mt).stream()
                        .map(Score::copySubclassFields)
                        .toList();
                case POPEVE -> getPopEveScores(acc, pos, mt).stream()
                        .map(Score::copySubclassFields)
                        .toList();
            };
        }

        // All types - executed sequentially but within same transaction (same connection)
        List<Score> results = new ArrayList<>();

        getConservScore(acc, pos)
                .map(Score::copySubclassFields)
                .ifPresent(results::add);

        results.addAll(getEveScores(acc, pos, mt).stream()
                .map(Score::copySubclassFields)
                .toList());

        results.addAll(getEsmScores(acc, pos, mt).stream()
                .map(Score::copySubclassFields)
                .toList());

        results.addAll(getAmScores(acc, pos, mt).stream()
                .map(Score::copySubclassFields)
                .toList());

        results.addAll(getPopEveScores(acc, pos, mt).stream()
                .map(Score::copySubclassFields)
                .toList());

        return results;
    }

    /**
     * Get all scores for a single accession (all positions)
     * @Transactional ensures all queries use the same connection
     *
     * Used in AnnotationFetcher.preloadOptionalAnnotations()
     *          \_used in DownloadProcessor.processAndWriteCsv()
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "scoresByAccession", key = "#accession")
    public List<Score> getScores(String accession) {
        if (accession == null || accession.isBlank()) {
            return Collections.emptyList();
        }

        List<Score> results = new ArrayList<>();
        results.addAll(getConservScoresByAccession(accession));
        results.addAll(getEveScoresByAccession(accession));
        results.addAll(getEsmScoresByAccession(accession));
        results.addAll(getAmScoresByAccession(accession));
        results.addAll(getPopEveScoresByAccession(accession));

        return results;
    }

    /**
     * Get scores for multiple accession-position pairs
     * @Transactional ensures all queries use the same connection
     *
     * Used in
     *  - ScoreRepo.getMappingScores() -> used in InputMapper.loadCoreMappingAndScores()
     *  - ScoreRepo.getAnnotationScores() -> used in InputMapper.preloadOptionalAnnotations() & .getAPIFunctionalData()
     */
    @Transactional(readOnly = true)
    public List<Score> getScores(String[] accessions, Integer[] positions, Set<ScoreType> types) {
        if (accessions == null || accessions.length == 0) {
            return Collections.emptyList();
        }

        // If types is null or empty, query all ScoreTypes
        Set<ScoreType> typesToQuery = (types == null || types.isEmpty())
                ? EnumSet.allOf(ScoreType.class)
                : types;

        List<Score> results = new ArrayList<>();

        if (typesToQuery.contains(ScoreType.CONSERV)) {
            results.addAll(getConservScoresBatch(accessions, positions));
        }
        if (typesToQuery.contains(ScoreType.EVE)) {
            results.addAll(getEveScoresBatch(accessions, positions));
        }
        if (typesToQuery.contains(ScoreType.ESM)) {
            results.addAll(getEsmScoresBatch(accessions, positions));
        }
        if (typesToQuery.contains(ScoreType.AM)) {
            results.addAll(getAmScoresBatch(accessions, positions));
        }
        if (typesToQuery.contains(ScoreType.POPEVE)) {
            results.addAll(getPopEveScoresBatch(accessions, positions));
        }

        return results;
    }

    /**
     * Get only AlphaMissense scores for multiple accession-position pairs
     * No @Transactional needed - delegates to getScores() which is already transactional
     */
    public List<Score> getMappingScores(String[] accessions, Integer[] positions) {
        return getScores(accessions, positions, Set.of(ScoreType.AM));
    }

    /**
     * Get annotation scores for multiple accession-position pairs
     * No @Transactional needed - delegates to getScores() which is already transactional
     */
    public List<Score> getAnnotationScores(String[] accessions, Integer[] positions) {
        return getScores(accessions, positions,
                Set.of(ScoreType.CONSERV, ScoreType.EVE, ScoreType.ESM, ScoreType.POPEVE));
    }

    // ============================================================================
    // ROW MAPPERS
    // ============================================================================

    private final RowMapper<ConservScore> conservScoreMapper = (rs, rowNum) ->
            new ConservScore(
                    rs.getString("accession"),
                    rs.getInt("position"),
                    rs.getDouble("score")
            );

    private final RowMapper<EveScore> eveScoreMapper = (rs, rowNum) ->
            new EveScore(
                    rs.getString("accession"),
                    rs.getInt("position"),
                    rs.getString("mt_aa"),
                    rs.getDouble("score"),
                    EveClass.fromValue(rs.getObject("class", Integer.class))
            );

    private final RowMapper<EsmScore> esmScoreMapper = (rs, rowNum) ->
            new EsmScore(
                    rs.getString("accession"),
                    rs.getInt("position"),
                    rs.getString("mt_aa"),
                    rs.getDouble("score")
            );

    private final RowMapper<AmScore> amScoreMapper = (rs, rowNum) ->
            new AmScore(
                    rs.getString("accession"),
                    rs.getInt("position"),
                    rs.getString("mt_aa"),
                    rs.getDouble("am_pathogenicity"),
                    AmClass.parseOrNull(rs.getObject("am_class", Integer.class))
            );

    private final RowMapper<PopEveScore> popEveScoreMapper = (rs, rowNum) ->
            new PopEveScore(
                    rs.getString("accession"),
                    rs.getInt("position"),
                    rs.getString("wt_aa"),
                    rs.getString("mt_aa"),
                    getDoubleOrNull(rs, "gap_freq"),
                    getDoubleOrNull(rs, "popeve"),
                    getDoubleOrNull(rs, "popped_eve"),
                    getDoubleOrNull(rs, "popped_esm_1v"),
                    getDoubleOrNull(rs, "eve"),
                    getDoubleOrNull(rs, "esm_1v")
            );

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    private Double getDoubleOrNull(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }
}
