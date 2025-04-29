package uk.ac.ebi.protvar.repo;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class UniprotRefseqRepo {

    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Value("${tbl.uprefseq}")
    private String uniprotRefseqTable;

    /**
     * Check the number of RefSeq accession without version
     * select no_ver, count(*) from (
     * select split_part(refseq_acc, '.', 1) as no_ver, * from uniprot_refseq) t
     * group by no_ver
     * having count(*) > 1
     */

    public Map<String, List<String>> getRefSeqUniprotMap(Set<String> rsAccs) {
        if (rsAccs == null || rsAccs.isEmpty())
            return new HashMap();
        String sql = String.format("SELECT * FROM %s WHERE refseq_acc IN (:rsAccs)",
                uniprotRefseqTable);
        SqlParameterSource parameters = new MapSqlParameterSource("rsAccs", rsAccs);
        return namedParameterJdbcTemplate.query(sql, parameters, new ResultSetExtractor<Map>() {
            @Override
            public Map extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<String, List<String>> resultMap = new HashMap();
                while(rs.next()){
                    String upAcc = rs.getString("uniprot_acc");
                    String rsAcc = rs.getString("refseq_acc");
                    if (!resultMap.containsKey(rsAcc))
                        resultMap.put(rsAcc, new ArrayList<>());
                    resultMap.get(rsAcc).add(upAcc);
                }
                return resultMap;
            }
        });
    }

    public TreeMap<String, List<String>> getRefSeqNoVerUniprotMap(Set<String> rsAccs) {
        if (rsAccs == null || rsAccs.isEmpty())
            return new TreeMap<>();
        String sql = String.format("SELECT * FROM %s WHERE split_part(refseq_acc, '.', 1) IN (:rsAccs)",
                uniprotRefseqTable);
        SqlParameterSource parameters = new MapSqlParameterSource("rsAccs", rsAccs);
        return namedParameterJdbcTemplate.query(sql, parameters, new ResultSetExtractor<TreeMap>() {
            @Override
            public TreeMap extractData(ResultSet rs) throws SQLException, DataAccessException {
                TreeMap<String, List<String>> resultMap = new TreeMap<>();
                while(rs.next()){
                    String upAcc = rs.getString("uniprot_acc");
                    String rsAcc = rs.getString("refseq_acc");
                    if (!resultMap.containsKey(rsAcc))
                        resultMap.put(rsAcc, new ArrayList<>());
                    resultMap.get(rsAcc).add(upAcc);
                }
                return resultMap;
            }
        });
    }
}