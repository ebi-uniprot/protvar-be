package uk.ac.ebi.protvar.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Derived from component: protvar-import
 * Release config, incl for core database table names.
 * Dynamically determines table names using a prefix and release version.
 */
@Component
public class ReleaseConfig {

    @Value("${uniprot.release}")
    @Getter
    private String uniprotRelease;

    @Value("${ensembl.release}")
    @Getter
    private String ensemblRelease;

    @Value("${tbl.prefix}")
    @Getter
    private String tblPrefix;

    @Value("${tbl.sequence}")
    private String sequenceTable;

    @Value("${tbl.exon.boundary.chr}")
    private String exonBoundaryTable;

    @Value("${tbl.mapping}")
    private String mappingTable;

    @Value("${tbl.upentry}")
    private String uniprotEntryTable;

    @Value("${tbl.uprefseq}")
    private String uniprotRefseqTable;

    @Value("${tbl.release}")
    @Getter
    private String releaseTable;

    // Newly added table for protvar-be
    @Value("${tbl.crossmap}")
    @Getter
    private String crossmapTable;

    @Value("${tbl.cadd}")
    @Getter
    private String caddTable;

    @Value("${tbl.clinvar}")
    @Getter
    private String clinvarTable;

    @Value("${tbl.cosmic}")
    @Getter
    private String cosmicTable;

    @Value("${tbl.dbsnp}")
    @Getter
    private String dbsnpTable;

    @Value("${tbl.variation}")
    @Getter
    private String variationTable;

    @Value("${tbl.stats}")
    @Getter
    private String statsTable;


    public String getSequenceTable() {
        return getFullTableName(sequenceTable);
    }

    public String getExonBoundaryTable(String chr) {
        return getFullTableName(String.format(exonBoundaryTable, chr));
    }
    public String getMappingTable() {
        return getFullTableName(mappingTable);
    }

    public String getUniprotEntryTable() {
        return getFullTableName(uniprotEntryTable);
    }

    public String getUniprotRefseqTable() {
        return getFullTableName(uniprotRefseqTable);
    }

    private String getFullTableName(String tableName) {
        if (tblPrefix != null && !tblPrefix.isEmpty()) {
            return String.format("%s_%s", tblPrefix, tableName);
        }
        return String.format("rel_%s_%s", uniprotRelease, tableName);
    }
}
