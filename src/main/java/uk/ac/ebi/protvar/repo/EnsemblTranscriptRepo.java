package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.EnsemblTranscript;

import java.util.List;

/**
 * Reads the {@code rel_<rel>_ensembl_transcript} dim table. is_mane_select is aliased AS mane_select so
 * BeanPropertyRowMapper binds it to EnsemblTranscript.maneSelect.
 */
@Repository
@RequiredArgsConstructor
public class EnsemblTranscriptRepo {

    private final JdbcTemplate jdbcTemplate;

    @Value("${tbl.ensembl.transcript}")
    private String ensemblTranscriptTable;

    public List<EnsemblTranscript> findAll() {
        String sql = "SELECT ensg, enst, enstv, ensp, enspv, accession, is_mane_select AS mane_select FROM "
                + ensemblTranscriptTable + " ORDER BY accession, enst";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(EnsemblTranscript.class));
    }
}
