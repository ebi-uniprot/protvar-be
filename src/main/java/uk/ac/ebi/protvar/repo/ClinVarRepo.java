package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.ClinVar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

@Repository
@AllArgsConstructor
public class ClinVarRepo {

    public static final String SELECT_CLINVAR_WHERE_RCV_IN = "SELECT DISTINCT * FROM clinvar WHERE rcv in (:rcv)";
    public static final String SELECT_CLINVAR_WHERE_VCV_IN = "SELECT DISTINCT * FROM clinvar WHERE vcv in (:vcv)";

    private NamedParameterJdbcTemplate jdbcTemplate;

    public List<ClinVar> getByRCV(Set<String> rcv) {
        SqlParameterSource parameters = new MapSqlParameterSource("rcv", rcv);
        return jdbcTemplate.query(SELECT_CLINVAR_WHERE_RCV_IN, parameters, (rs, rowNum) -> createClinVar(rs));
    }

    public List<ClinVar> getByVCV(Set<String> vcv) {
        SqlParameterSource parameters = new MapSqlParameterSource("vcv", vcv);
        return jdbcTemplate.query(SELECT_CLINVAR_WHERE_VCV_IN, parameters, (rs, rowNum) -> createClinVar(rs));
    }

    private ClinVar createClinVar(ResultSet rs) throws SQLException {
        return new ClinVar(rs.getString("rcv"), rs.getString("vcv"), rs.getString("chr"),
                rs.getInt("pos"), rs.getString("ref"), rs.getString("alt"));
    }
}