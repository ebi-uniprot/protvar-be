package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.ClinVar;
import uk.ac.ebi.protvar.model.data.ClinVarExtended;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ClinVarRepo {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${tbl.clinvar}")
    private String clinvarTable;

    @Value("${tbl.clinvar.extended}")
    private String clinvarExtendedTable;

    public List<ClinVar> getByRCV(List<Object[]> rcvs) {
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

    public List<ClinVar> getByVCV(List<Object[]> vcvs) {
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
    public Map<String, List<ClinVarExtended>> getByRCVMap(List<String> rcvs) {
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
        SqlParameterSource parameters = new MapSqlParameterSource()
                // Convert Java List to PostgreSQL ARRAY (Properly Bind Parameter)
                .addValue("rcvs", rcvs.toArray(new String[0]), Types.ARRAY);

        // Query the database and map the results to ClinVarExtended objects
        List<ClinVarExtended> clinVarExtendedList = namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) -> getClinVarExtendedFromRs(rs));

        // Initialize the result map with empty lists for each RCV
        Map<String, List<ClinVarExtended>> result = new HashMap<>();
        rcvs.forEach(rcv -> result.put(rcv, new ArrayList<>()));

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
    public Map<String, List<ClinVarExtended>> getByVCVMap(List<String> vcvs) {
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
        SqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("vcvs", vcvs.toArray(new String[0]), Types.ARRAY);

        // Query the database and map the results to ClinVarExtended objects
        List<ClinVarExtended> clinVarExtendedList = namedParameterJdbcTemplate.query(sql, parameters, (rs, rowNum) ->  getClinVarExtendedFromRs(rs));

        // Initialize the Map with the input VCVs
        Map<String, List<ClinVarExtended>> result = new HashMap<>();
        vcvs.forEach(vcv -> result.put(vcv, new ArrayList<>()));

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