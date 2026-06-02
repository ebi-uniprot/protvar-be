package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;
import uk.ac.ebi.protvar.types.IdentifierType;
import java.util.regex.Pattern;

public class InputTypeResolver {
    // Matches input IDs (exact 32-character lowercase hex string)
    public static final Pattern INPUT_ID_REGEX =
            Pattern.compile("^[a-f0-9]{32}$", Pattern.CASE_INSENSITIVE);

    // Matches Ensembl gene, transcript, protein, or exon IDs
    // ENSG00000139618, ENST00000380152.4, ENSP00000369497, ENSE00000123456
    public static final Pattern ENSEMBL_REGEX =
            Pattern.compile("^ENS[GTPE]\\d{11}(\\.\\d+)?$", Pattern.CASE_INSENSITIVE);

    // Matches UniProt accession (6 or 10 character format, optional isoform suffix)
    // P12345, Q9H9K5-2, A0A1B2C3D4
    public static final Pattern UNIPROT_REGEX =
            Pattern.compile(ProteinParser.VALID_UNIPROT, Pattern.CASE_INSENSITIVE);

    // Matches PDB structure IDs (4 characters: digit + 3 alphanumeric)
    // 1ABC, 2XYZ, 7A1B
    // New accession codes: 12 alphanumeric characters with prefix pdb_
    // e.g. pdb_00001abc
    public static final Pattern PDB_REGEX =
            Pattern.compile("^[0-9][A-Za-z0-9]{3}$");

    // Matches RefSeq identifiers (supported and unsupported)
    // Includes NM_ (mRNA), NP_ (protein), but not NC_ (chromosome)
    // But excludes those that look like HGVS (contain colon)
    public static final Pattern REFSEQ_REGEX =
            Pattern.compile("^(NC|NM|NP|NG|LRG|NR)_\\d{6,}(\\.\\d+)?$", Pattern.CASE_INSENSITIVE);

    // Matches gene symbols
    // Must start with letter, 2-20 characters, allows letters, numbers, hyphens, underscores
    // Excludes common variant prefixes (rs, RCV, COSV, etc.)
    public static final Pattern GENE_REGEX =
            Pattern.compile("^[A-Za-z][A-Za-z0-9\\-_]{1,19}$"); // slightly more restrictive than as used in HGVScParser

    // Common single-word variant prefixes that should NOT be treated as genes
    private static final Pattern VARIANT_PREFIX_REGEX =
            Pattern.compile("^(rs|RCV|VCV|COS[VMN]|NC_|NM_|NP_|chr).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern VARIANT_PREFIX_REGEX_ =
            // variant IDs; HGVS notation with RefSeq reference part;
            // genomic variant start chromosome; protein variant start UniProt accession
            Pattern.compile("^(rs|RCV|VCV|COS[VMN]|NC_|NM_|NP_|NG_|LRG_|NR_|chr|([1-9]|1[0-9]|2[0-2]|MT|mit|mtDNA|mitochondria|mitochondrion)(?=\\s|-)|" + ProteinParser.VALID_UNIPROT + "(?=\\s)).*", Pattern.CASE_INSENSITIVE);

    /**
     * Resolve a string to its biological {@link IdentifierType}, skipping variant and result-ID patterns.
     * Returns null if the input looks like a variant query, an uploaded result ID, or is unrecognisable.
     * Falls back to GENE for values that match the gene pattern.
     *
     * <p>Use this method when resolving identifiers for browse queries (POST /mapping with ids[]).
     */
    public static IdentifierType resolveIdentifier(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        String trimmed = input.trim();
        if (!isSingleLine(trimmed)) return null;

        // Skip INPUT_ID (32-char hex) — those go via resultId, not ids[]
        if (INPUT_ID_REGEX.matcher(trimmed).matches()) return null;

        if (ENSEMBL_REGEX.matcher(trimmed).matches()) return IdentifierType.ENSEMBL;
        if (PDB_REGEX.matcher(trimmed).matches()) return IdentifierType.PDB;
        if (UNIPROT_REGEX.matcher(trimmed).matches()) return IdentifierType.UNIPROT;

        // Skip inputs that parse as variants
        VariantInput parsed = VariantParser.parse(trimmed);
        if (parsed != null && parsed.getFormat() != VariantFormat.INVALID) return null;

        if (REFSEQ_REGEX.matcher(trimmed).matches()) return IdentifierType.REFSEQ;

        if (GENE_REGEX.matcher(trimmed).matches() && !VARIANT_PREFIX_REGEX.matcher(trimmed).matches()) {
            return IdentifierType.GENE;
        }
        return null;
    }

    public static boolean isSingleLine(String input) {
        return input != null && !input.contains("\n") && !input.contains("\r");
    }
}