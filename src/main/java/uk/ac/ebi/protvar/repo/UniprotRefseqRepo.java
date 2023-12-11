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

    public static final String SELECT_UP_RS_WHERE_RSACC_IN = "SELECT * FROM uniprot_refseq WHERE refseq_acc in (:rsAccs)";

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
}