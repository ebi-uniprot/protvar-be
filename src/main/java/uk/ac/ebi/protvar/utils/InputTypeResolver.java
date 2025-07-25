package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.types.InputType;
import java.util.regex.Pattern;

public class InputTypeResolver {

    // Matches Ensembl gene, transcript, or protein IDs (e.g. ENSG00000139618, ENST00000380152.4)
    public static final Pattern ENSEMBL_REGEX = Pattern.compile("^(ENS[GTP])(\\d{11})(?:\\.(\\d+))?$", Pattern.CASE_INSENSITIVE);

    // Matches UniProt accession (6 or 10 character format, optional isoform suffix e.g. Q9H9K5-2)
    private static final Pattern UNIPROT_REGEX = Pattern.compile(
            "^([OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z]([0-9][A-Z][A-Z0-9]{2}){1,2}[0-9])(-[1-9][0-9]*)?$",
            Pattern.CASE_INSENSITIVE
    );

    // Matches PDB IDs
    private static final Pattern PDB_REGEX = Pattern.compile("^[0-9][A-Za-z0-9]{3}$");

    // Matches RefSeq mRNA (NM) and protein (NP) identifiers

    public static final Pattern REFSEQ_REGEX =
            Pattern.compile("^(NM|NP)_\\d{6,}(\\.\\d+)?$", Pattern.CASE_INSENSITIVE);

    // Matches input IDs (exact 32-character lowercase hex string)
    private static final Pattern INPUT_ID_REGEX =
            Pattern.compile("^[a-f0-9]{32}$", Pattern.CASE_INSENSITIVE);

    // Matches gene symbols (e.g. BRCA1, TP53, MYC), permissive pattern
    private static final Pattern GENE_REGEX =
            Pattern.compile("^[A-Za-z0-9\\-_]{2,}$");

    public static InputType resolve(String input) {
        // If input is null or empty, return null
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        String trimmed = input.trim();

        // Check most specific to most generic patterns
        if (ENSEMBL_REGEX.matcher(trimmed).matches()) {
            return InputType.ENSEMBL;
        } else if (UNIPROT_REGEX.matcher(trimmed).matches()) {
            return InputType.UNIPROT;
        } else if (PDB_REGEX.matcher(trimmed).matches()) {
            return InputType.PDB;
        } else if (REFSEQ_REGEX.matcher(trimmed).matches()) {
            return InputType.REFSEQ;
        } else if (INPUT_ID_REGEX.matcher(trimmed).matches()) {
            return InputType.INPUT_ID;
        } else if (GENE_REGEX.matcher(trimmed).matches()) {
            return InputType.GENE;
        }

        // Try parsing as a variant
        if (isSingleLine(trimmed)) {
            VariantInput parsed = VariantParser.parse(trimmed);
            if (parsed != null && parsed.isValid()) {
                return InputType.VARIANT;
            }
        }

        return null;
    }

    public static boolean isSingleLine(String input) {
        return input != null && !input.contains("\n") && !input.contains("\r");
    }
}