package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.config.ReleaseConfig;
import uk.ac.ebi.protvar.model.data.UniprotEntry;

import java.util.List;

@Repository
@AllArgsConstructor
public class UniprotEntryRepo {
    private ReleaseConfig releaseConfig;
    private JdbcTemplate jdbcTemplate;

    public List<UniprotEntry> findAll() {
        String sql = "SELECT * FROM " + releaseConfig.getUniprotEntryTable();
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper(UniprotEntry.class));
    }
}
