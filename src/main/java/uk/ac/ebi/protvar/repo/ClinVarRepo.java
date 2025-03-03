package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.ClinVar;
import uk.ac.ebi.protvar.model.data.ClinVarExtended;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@AllArgsConstructor
public class ClinVarRepo {
    @Value("${tbl.clinvar}")
    private String clinvarTable;

    @Value("${tbl.clinvar.extended}")
    private String clinvarExtendedTable;

    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<ClinVar> getByRCV(Set<Object[]> rcvs) {
        if (rcvs == null || rcvs.isEmpty())
            return new ArrayList<>();
        String sql = String.format("""
                SELECT DISTINCT c.rcv, c.chr, c.pos, c.ref, c.alt 
                FROM %s c
                INNER JOIN (VALUES :rcvs) AS t(rcv)
                ON t.rcv=c.rcv
                """, clinvarTable);
        SqlParameterSource parameters = new MapSqlParameterSource("rcvs", rcvs);
        return namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> getClinVaFromRs(rs));
    }

    public List<ClinVar> getByVCV(Set<Object[]> vcvs) {
        if (vcvs == null || vcvs.isEmpty())
            return new ArrayList<>();
        String sql = String.format("""
                SELECT DISTINCT c.vcv, c.chr, c.pos, c.ref, c.alt 
                FROM %s c
                INNER JOIN (VALUES :vcvs) AS t(vcv)
                ON t.vcv=c.vcv
                """, clinvarTable);
        SqlParameterSource parameters = new MapSqlParameterSource("vcvs", vcvs);
        return namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> getClinVaFromRs(rs));
    }

    // New clinvar extended table queries
    public Map<String, List<ClinVarExtended>> getByRCVMap(Set<String> rcvs) {
        if (rcvs == null || rcvs.isEmpty()) {
            return new HashMap<>();
        }

        String sql = String.format("""
            SELECT DISTINCT c.rcvs, c.vcv, c.chr, c.pos, c.ref, c.alt
            FROM %s c
            WHERE EXISTS (
                SELECT 1
                FROM unnest(c.rcvs) AS rcv
                WHERE rcv = ANY(:rcvs)
            )
            """, clinvarExtendedTable);

        // Create the parameter map for the query
        SqlParameterSource parameters = new MapSqlParameterSource("rcvs", rcvs.toArray());

        // Query the database and map the results to ClinVarExtended objects
        List<ClinVarExtended> clinVarExtendedList = namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> getClinVarExtendedFromRs(rs));

        // Initialize the Map with the input RCVs
        Map<String, List<ClinVarExtended>> result = new HashMap<>();

        // Initialize empty lists for each RCV in the input
        for (String rcv : rcvs) {
            result.put(rcv, new ArrayList<>());
        }

        // Add ClinVarExtended to the corresponding RCV key in the map
        for (ClinVarExtended clinVarExtended : clinVarExtendedList) {
            for (String rcv : rcvs) {
                if (clinVarExtended.getRcvs().contains(rcv)) {
                    result.get(rcv).add(clinVarExtended);
                }
            }
        }
        return result;
    }

    // New clinvar extended table query for vcv column
    public Map<String, List<ClinVarExtended>> getByVCVMap(Set<String> vcvs) {
        if (vcvs == null || vcvs.isEmpty()) {
            return new HashMap<>();
        }
        // TODO review other places where ANY might be better than INNER JOIN

        String sql = String.format("""
            SELECT DISTINCT c.rcvs, c.vcv, c.chr, c.pos, c.ref, c.alt
            FROM %s c
            WHERE c.vcv = ANY(:vcvs)
            """, clinvarExtendedTable);

        // Create the parameter map for the query
        SqlParameterSource parameters = new MapSqlParameterSource("vcvs", vcvs.toArray());

        // Query the database and map the results to ClinVarExtended objects
        List<ClinVarExtended> clinVarExtendedList = namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) ->  getClinVarExtendedFromRs(rs));

        // Initialize the Map with the input VCVs
        Map<String, List<ClinVarExtended>> result = new HashMap<>();

        // Initialize empty lists for each VCV in the input
        for (String vcv : vcvs) {
            result.put(vcv, new ArrayList<>());
        }

        // Add ClinVarExtended to the corresponding VCV key in the map
        for (ClinVarExtended clinVarExtended : clinVarExtendedList) {
            for (String vcv : vcvs) {
                if (clinVarExtended.getVcv().equals(vcv)) {
                    result.get(vcv).add(clinVarExtended);
                }
            }
        }

        return result;
    }

    public ClinVarExtended getClinVarExtendedFromRs(ResultSet rs) throws SQLException {
        return ClinVarExtended.builder()
                .rcvs(Arrays.asList((String[]) rs.getArray("rcvs").getArray()))
                .vcv(rs.getString("vcv"))
                .chr(rs.getString("chr"))
                .pos(rs.getInt("pos"))
                .ref(rs.getString("ref"))
                .alt(rs.getString("alt"))
                .build();
    }

    public ClinVar getClinVaFromRs(ResultSet rs) throws SQLException {
        return ClinVar.builder()
                .rcv(rs.getString("rcv"))
                .vcv(rs.getString("vcv"))
                .chr(rs.getString("chr"))
                .pos(rs.getInt("pos"))
                .ref(rs.getString("ref"))
                .alt(rs.getString("alt"))
                .build();
    }
}