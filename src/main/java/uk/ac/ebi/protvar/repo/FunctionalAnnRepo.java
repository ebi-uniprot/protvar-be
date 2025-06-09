package uk.ac.ebi.protvar.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.uniprot.domain.entry.UPEntry;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class FunctionalAnnRepo {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tbl.ann.fun}")
    private String functionTable;


    // Caching is managed at the service layer after UPEntry is converted to FunctionalInfo
    // using CacheManager as caching a list of accessions is tricky (see FunctionalAnnService)
    //@Cacheable(value = "UPEntry", key = "#accession")
    public UPEntry getEntry(String accession) {
        if (accession == null) return null;

        String sql = String.format("SELECT entry FROM %s WHERE accession = :accession", functionTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accession", accession);

        try {
            String json = jdbcTemplate.queryForObject(sql, params, String.class);
            return objectMapper.readValue(json, UPEntry.class);
        } catch (EmptyResultDataAccessException e) {
            return null; // not found
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public Map<String, UPEntry> getEntries(List<String> accessions) {
        if (accessions == null || accessions.isEmpty()) return Collections.emptyMap();

        String sql = String.format("""
            SELECT accession, entry
            FROM %s
            WHERE accession IN (:accessions)
            """, functionTable);
        MapSqlParameterSource params = new MapSqlParameterSource("accessions", accessions);

        return jdbcTemplate.query(sql, params, rs -> {
            Map<String, UPEntry> result = new HashMap<>();
            while (rs.next()) {
                String accession = rs.getString("accession");
                String json = rs.getString("entry");
                try {
                    UPEntry entry = objectMapper.readValue(json, UPEntry.class);
                    result.put(accession, entry);
                } catch (JsonProcessingException e) {
                    return null;
                }
            }
            return result;
        });
    }
}
