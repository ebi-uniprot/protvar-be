package uk.ac.ebi.protvar.repo;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class UniprotRefseqRepo {

    public static final String SELECT_UP_RS_WHERE_RSACC_IN = "SELECT * FROM uniprot_refseq WHERE refseq_acc IN (:rsAccs)";

    public static final String SELECT_WITHOUT_RS_VERSION = "SELECT * FROM uniprot_refseq WHERE split_part(refseq_acc, '.', 1) IN (:rsAccs)";

    /**
     * Check the number of RefSeq accession without version
     * select no_ver, count(*) from (
     * select split_part(refseq_acc, '.', 1) as no_ver, * from uniprot_refseq) t
     * group by no_ver
     * having count(*) > 1
     */
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public Map<String, List<String>> getRefSeqUniprotMap(Set<String> rsAccs) {
        if (rsAccs == null || rsAccs.isEmpty())
            return new HashMap();
        SqlParameterSource parameters = new MapSqlParameterSource("rsAccs", rsAccs);
        return namedParameterJdbcTemplate.query(SELECT_UP_RS_WHERE_RSACC_IN, parameters, new ResultSetExtractor<Map>() {
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
        SqlParameterSource parameters = new MapSqlParameterSource("rsAccs", rsAccs);
        return namedParameterJdbcTemplate.query(SELECT_WITHOUT_RS_VERSION, parameters, new ResultSetExtractor<TreeMap>() {
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