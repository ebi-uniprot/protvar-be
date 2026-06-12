package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.Protein;

import java.util.List;

/**
 * Reads the {@code rel_<rel>_protein} dim table. is_canonical is aliased AS canonical so
 * BeanPropertyRowMapper binds it to Protein.isCanonical (the "is_" prefix otherwise won't match).
 */
@Repository
@RequiredArgsConstructor
public class ProteinRepo {

    private final JdbcTemplate jdbcTemplate;

    @Value("${tbl.protein}")
    private String proteinTable;

    public List<Protein> findAll() {
        String sql = "SELECT accession, protein_name, gene_name, is_canonical AS canonical, length FROM "
                + proteinTable + " ORDER BY accession";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(Protein.class));
    }
}
