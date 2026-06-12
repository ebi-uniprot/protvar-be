package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.UniprotAccession;

import java.util.List;

/**
 * Reads the {@code rel_<rel>_uniprot_accession} reference table (full known set + is_canonical).
 * is_canonical is aliased AS canonical so BeanPropertyRowMapper binds it to UniprotAccession.isCanonical.
 * (Was UniprotEntryRepo against the canonical-only uniprot_entry.)
 */
@Repository
@RequiredArgsConstructor
public class UniprotAccessionRepo {

    private final JdbcTemplate jdbcTemplate;

    @Value("${tbl.uniprot.accession}")
    private String uniprotAccessionTable;

    public List<UniprotAccession> findAll() {
        String sql = "SELECT accession, is_canonical AS canonical FROM " + uniprotAccessionTable + " ORDER BY accession";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(UniprotAccession.class));
    }
}
