package uk.ac.ebi.protvar.processor;

import java.util.List;

import static uk.ac.ebi.protvar.processor.CsvHeaders.Group.*;

/**
 * The download file's columns, grouped by what the data is. Variant and Mapping are always in the file; the other
 * groups are added on request. Which columns are present depends on the request, so the file's first row names them
 * (read the columns by name, not by position).
 */
public final class CsvHeaders {

    private CsvHeaders() {
        // Utility class; prevent instantiation
    }

    public enum Group { VARIANT, MAPPING, FUNCTION, PREDICTION, POPULATION, STRUCTURE }

    /**
     * @param table the result-table column (FE resultColumns id) the file column shows: kept when the request asks
     *              for only the table's shown columns. {@link #ALWAYS}: always kept; null: not in the table.
     */
    public record Column(String name, Group group, String table) {}

    public static final String ALWAYS = "";

    public static final List<Column> COLUMNS = List.of(
            // Variant: the line as entered and the genomic variant it was read as
            new Column("Input", VARIANT, ALWAYS),
            new Column("Notes", VARIANT, ALWAYS),
            new Column("ID", VARIANT, "id"),
            new Column("Chromosome", VARIANT, "position"),
            new Column("Position", VARIANT, "position"),
            new Column("Ref", VARIANT, "position"),
            new Column("Alt", VARIANT, "position"),
            // Mapping, in the result table's order
            new Column("Gene", MAPPING, "gene"),
            new Column("Codon_change", MAPPING, "codon"),
            new Column("Strand", MAPPING, "codon"),
            new Column("CADD", MAPPING, "cadd"),
            new Column("AVI", MAPPING, "avi"),
            new Column("Pangolin", MAPPING, "pangolin"),
            new Column("Isoform", MAPPING, "isoform"),
            new Column("Other_isoforms", MAPPING, "isoform"),
            new Column("Transcripts", MAPPING, null),
            new Column("Protein", MAPPING, "protein"),
            new Column("AA_position", MAPPING, "aaChange"),
            new Column("AA_change", MAPPING, "aaChange"),
            new Column("Consequence", MAPPING, "consequence"),
            new Column("popEVE", MAPPING, "popeve"),
            new Column("AlphaMissense", MAPPING, "am"),
            // Protein function (UniProt)
            new Column("Residue_features", FUNCTION, null),
            new Column("Region_features", FUNCTION, null),
            new Column("Existence_evidence", FUNCTION, null),
            new Column("Protein_length", FUNCTION, null),
            new Column("Entry_updated", FUNCTION, null),
            new Column("Sequence_updated", FUNCTION, null),
            new Column("Catalytic_activity", FUNCTION, null),
            new Column("Complex", FUNCTION, null),
            new Column("Subcellular_location", FUNCTION, null),
            new Column("Family", FUNCTION, null),
            new Column("Interactions", FUNCTION, null),
            // Predictions
            new Column("Conservation", PREDICTION, null),
            new Column("ESM1b", PREDICTION, null),
            new Column("FoldX", PREDICTION, null),
            new Column("Pockets", PREDICTION, null),
            new Column("Interfaces", PREDICTION, null),
            // Population
            new Column("gnomAD_AF", POPULATION, null),
            new Column("HGVS_g", POPULATION, null),
            new Column("Cytoband", POPULATION, null),
            new Column("Xrefs", POPULATION, null),
            new Column("Diseases", POPULATION, null),
            new Column("Colocated_variants", POPULATION, null),
            // Structures
            new Column("PDB_structures", STRUCTURE, null)
    );

    public static List<Column> of(Group group) {
        return COLUMNS.stream().filter(c -> c.group() == group).toList();
    }

    public static int size(Group group) {
        return (int) COLUMNS.stream().filter(c -> c.group() == group).count();
    }
}
