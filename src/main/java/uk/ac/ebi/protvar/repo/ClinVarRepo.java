package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.ClinVar;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Repository
@AllArgsConstructor
public class ClinVarRepo {
    public static final String SELECT_CLINVAR_WHERE_RCV_IN = """
        SELECT DISTINCT c.* FROM clinvar c
        INNER JOIN (VALUES :rcvs) AS t(rcv)
        ON t.rcv=c.rcv
        """;
    public static final String SELECT_CLINVAR_WHERE_VCV_IN = """
        SELECT DISTINCT c.* FROM clinvar c
        INNER JOIN (VALUES :vcvs) AS t(vcv)
        ON t.vcv=c.vcv
        """;

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<ClinVar> getByRCV(Set<Object[]> rcvs) {
        if (rcvs == null || rcvs.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("rcvs", rcvs);
        return namedParameterJdbcTemplate.query(SELECT_CLINVAR_WHERE_RCV_IN, parameters, (rs, rowNum) -> createClinVar(rs));
    }

    public List<ClinVar> getByVCV(Set<Object[]> vcvs) {
        if (vcvs == null || vcvs.isEmpty())
            return new ArrayList<>();
        SqlParameterSource parameters = new MapSqlParameterSource("vcvs", vcvs);
        return namedParameterJdbcTemplate.query(SELECT_CLINVAR_WHERE_VCV_IN, parameters, (rs, rowNum) -> createClinVar(rs));
    }

    private ClinVar createClinVar(ResultSet rs) throws SQLException {
        ClinVar clinVar = new ClinVar();
        clinVar.setRcv(rs.getString("rcv"));
        clinVar.setVcv(rs.getString("vcv"));
        clinVar.setChr(rs.getString("chr"));
        clinVar.setPos(rs.getInt("pos"));
        clinVar.setRef(rs.getString("ref"));
        clinVar.setAlt(rs.getString("alt"));
        return clinVar;
    }
}