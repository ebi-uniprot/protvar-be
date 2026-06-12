package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.EnsemblGene;

import java.util.List;

/**
 * Reads the {@code rel_<rel>_ensembl_gene} dim table (reverse_strand maps to reverseStrand automatically).
 */
@Repository
@RequiredArgsConstructor
public class EnsemblGeneRepo {

    private final JdbcTemplate jdbcTemplate;

    @Value("${tbl.ensembl.gene}")
    private String ensemblGeneTable;

    public List<EnsemblGene> findAll() {
        String sql = "SELECT ensg, ensgv, chromosome, reverse_strand FROM " + ensemblGeneTable + " ORDER BY ensg";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnsemblGene.class));
    }
}
