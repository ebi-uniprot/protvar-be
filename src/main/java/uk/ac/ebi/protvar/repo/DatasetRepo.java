package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Ready-made datasets (table written by protvar-production release/datasets): name → the upload id of its lines.
 * A database without the table yet (a Delphix copy not refreshed since it was added) has no datasets.
 */
@Repository
@RequiredArgsConstructor
public class DatasetRepo {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetRepo.class);

    public record Dataset(String name, String title, String description, String sourceVersion, int lineCount,
                          String resultId) {}

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${tbl.dataset:dataset}")
    private String table;

    public List<Dataset> list() {
        try {
            return jdbcTemplate.query("SELECT name, title, description, source_version, line_count, result_id FROM "
                    + table + " ORDER BY name", (rs, i) -> new Dataset(rs.getString("name"), rs.getString("title"),
                    rs.getString("description"), rs.getString("source_version"), rs.getInt("line_count"),
                    rs.getString("result_id")));
        } catch (DataAccessException e) {
            LOGGER.info("No datasets ({}): {}", table, e.getMostSpecificCause().getMessage());
            return List.of();
        }
    }
}
