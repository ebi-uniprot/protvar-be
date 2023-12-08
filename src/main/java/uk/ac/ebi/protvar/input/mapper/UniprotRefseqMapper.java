package uk.ac.ebi.protvar.input.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import uk.ac.ebi.protvar.model.data.UniprotRefseq;

import java.util.*;

/**
 *
 * TODO the following should be part of g2p mapping import
 * TODO remove uniprot_refseq load from backend, add to main import
 * TODO only cache from imported tbl in backend
 *
 * Mapping between RefSeq accessions (primarily those with NM_ and NP_ prefixes)
 * and Uniprot accessions (via Ensembl identifiers)
 *
 * Ensembl core schema: homo_sapiens_core_109_38
 * 1. RefSeq identifiers are stored in the `xref` table (display_label field)
 * 2. Mappings between gene/transcript/translation objects and xrefs are in the `object_xref` table
 * 3. Joining `xref` with `object_xref` will give an `ensembl_id` (Ensembl identifiers - for
 *    a gene, transcript, or translation )
 *
 * select distinct ensembl_object_type from object_xref
 * -----------------------
 * | ensembl_object_type |
 * -----------------------
 * | Gene                |
 * | Transcript          |
 * | Translation         |
 * -----------------------
 *
 * Ensembl data model
 *
 *         Gene
 *          |
 *          v  1:m
 *      Transcript  ------> Exon
 *          ^            1:m
 *          |
 *          v  1:1
 *      Translation (Protein)
 *
 * Checking NM_ and NP_ prefix object type:
 *
 * select distinct ox.ensembl_object_type from xref x
 *          join object_xref ox on x.xref_id = ox.xref_id
 *          where display_label like 'NM\_%'
 * -----------------------
 * | Transcript          |
 * -----------------------
 *
 * select distinct ox.ensembl_object_type from xref x
 *          join object_xref ox on x.xref_id = ox.xref_id
 *          where display_label like 'NP\_%'
 * -----------------------
 * | Translation         |
 * -----------------------
 *
 * Also,
 * select distinct ox.ensembl_object_type
 * from xref x
 * join object_xref ox on x.xref_id = ox.xref_id
 * where external_db_id=2200
 * -----------------------
 * | Translation         |
 * -----------------------
 *
 * This shows that NM_ is linked with transcript, and NP_ with translation (protein).
 * Also, all entries from uniprot db (with an accession) are translation objects.
 *
 * Based on the Reference Sequence (RefSeq) book chapter table (not complete), we
 * confirm NM_ prefix is used for protein-coding transcripts, while NP_ prefix for
 * protein.
 * Accession    Molecule    Comment
 * prefix^      type
 * NC_          Genomic     Complete genomic molecule, usually reference assembly
 * NM_          mRNA        <Protein-coding transcripts>
 * NR_          RNA         Non-protein coding transcripts
 * NP_          <Protein>   Associated with an NM_ (transcript) or NC_ (chromo) accession
 *
 * Instead of filtering for NM_ or NP_ prefix, we could also use the external_db_id.
 *
 * external_db tbl
 * external_db_id,db_name
 * 1830,RefSeq_genomic
 * 1801,RefSeq_mRNA       <- instead of NM_ pref
 * 1820,RefSeq_rna
 * 1810,RefSeq_peptide    <- instead of NP_ pref
 *
 * The results will be the same, except for some YP_prefix (see below)
 *
 * select count(*) from xref where display_label like
 * 'NC\_%' -- 0
 * 'NM\_%' -- 63,468  <- SAME
 * 'NR\_%' -- 16,025
 * 'NP\_%' -- 64,732  <- ?
 *
 * select count(*) from xref where external_db_id=
 * 1830 -- 0
 * 1801 -- 63,468   <- SAME
 * 1820 -- 0
 * 1810 -- 64,745   <- ? +13 more with YP_ prefix (select * from xref where external_db_id=1810 and display_label not like 'NP\_%')
 *
 *
 */
@Repository
public class UniprotRefseqMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniprotRefseqMapper.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JdbcTemplate ensemblJdbcTemplate;


    public final static String PROTVAR_TBL = "uniprot_refseq";

    // uniprot-ensembl map
    public final static String UNIPROT_ENSEMBL = "select x.dbprimary_acc as uniprot_acc, ox.ensembl_id as translation_id "
            + "from xref x "
            + "join object_xref ox on x.xref_id = ox.xref_id "
            + "where external_db_id=2200 ";

    // refseq-ensembl map for NP_
    public final static String REFSEQ_ENSEMBL_NP = "select display_label as refseq_acc, ox.ensembl_id as translation_id "
            + "from xref x "
            + "join object_xref ox on x.xref_id = ox.xref_id "
            + "where display_label like 'NP\\_%'";

    // refseq-ensembl map for NM_
    public final static String REFSEQ_ENSEMBL_NM = "select display_label as refseq_acc, tl.translation_id as translation_id "
            + "from xref x "
            + "join object_xref ox on x.xref_id = ox.xref_id "
            + "join transcript tc on ox.ensembl_id = tc.transcript_id "
            + "join translation tl on tl.transcript_id = tc.transcript_id "
            + "where display_label like 'NM\\_%'";

    public final static String UNIPROT_REFSEQ = "select distinct uniprot_acc, refseq_acc from " +
            "(%s) t1 " + // refseq-ensembl map for NP_ or NM_
            "join " +
            "(%s) t2 " + // uniprot-ensembl map
            "on t1.translation_id=t2.translation_id";

    public Map<String, List<String>> uniprotRefseqMap = new TreeMap();


    public List<String> getUniprotAccs(String refseqAcc) {
        if (uniprotRefseqMap != null)
            return uniprotRefseqMap.get(refseqAcc);
        return null;
    }
    @EventListener(classes = ApplicationStartedEvent.class )
    public void load() {
        LOGGER.info("Loading Uniprot-Refseq map");
        boolean success = tryLoadFromProtVarTbl();
        if (!success) {
            success = tryLoadFromEnsemblDB();
            if (success) {
                persistMapIntoProtVarTbl();
            } else {
                LOGGER.warn("Could not load Uniprot-Refseq map");
            }
        }
    }

    private boolean tryLoadFromProtVarTbl() {
        uniprotRefseqMap.clear();
        return tryLoad("SELECT * FROM " + PROTVAR_TBL, jdbcTemplate);
    }

    private boolean tryLoadFromEnsemblDB() {
        uniprotRefseqMap.clear();
        String uniprotRefseqNP = String.format(UNIPROT_REFSEQ, REFSEQ_ENSEMBL_NP, UNIPROT_ENSEMBL);
        String uniprotRefseqNM = String.format(UNIPROT_REFSEQ, REFSEQ_ENSEMBL_NM, UNIPROT_ENSEMBL);
        boolean success = tryLoad(uniprotRefseqNP, ensemblJdbcTemplate);
        if (success)
            return tryLoad(uniprotRefseqNM, ensemblJdbcTemplate);
        return false;
    }

    private boolean tryLoad(String sql, JdbcTemplate template) {
        try {
            List<UniprotRefseq> uniprotRefseqList = template.query(sql,
                    (rs, rowNum) -> new UniprotRefseq(rs.getString("uniprot_acc"), rs.getString("refseq_acc")));
            uniprotRefseqList.forEach(uniprotRefseq -> {
                String key = uniprotRefseq.getRefseqAcc();
                if (!uniprotRefseqMap.containsKey(key))
                    uniprotRefseqMap.put(key, new ArrayList<>());
                uniprotRefseqMap.get(key).add(uniprotRefseq.getUniprotAcc());
            });
            //uniprotRefseqMap = uniprotRefseqList.stream().collect(Collectors.groupingBy(UniprotRefseq::getRefseqAcc));
            if (uniprotRefseqMap.size() > 0)
                return true;
        } catch (Exception ex) {
            LOGGER.error(ex.getMessage());
        }
        return false;
    }

    private void persistMapIntoProtVarTbl() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + PROTVAR_TBL +
                    " ( uniprot_acc varchar, refseq_acc varchar );");
            jdbcTemplate.execute("TRUNCATE TABLE " + PROTVAR_TBL);
            List<Object[]> data = new ArrayList<>();
            for (String key : uniprotRefseqMap.keySet()) {
                for (String uniprotAcc : uniprotRefseqMap.get(key))
                    data.add(new Object[]{uniprotAcc, key});
            }
            jdbcTemplate.batchUpdate("INSERT INTO " + PROTVAR_TBL + " (uniprot_acc, refseq_acc) VALUES (?, ?)",
                    data);
        }
        catch (Exception ex) {
            LOGGER.error("Error occurred when persisting Uniprot-Refseq map");
        }
    }
}
