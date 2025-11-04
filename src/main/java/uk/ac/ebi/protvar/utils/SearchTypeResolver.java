package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;
import uk.ac.ebi.protvar.types.SearchType;

import java.util.regex.Pattern;

public class SearchTypeResolver {
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

    public static SearchType resolve(String term) {
        // If term is null or empty, return null
        if (term == null || term.trim().isEmpty()) {
            return null;
        }

        String trimmed = term.trim();

        // Early exit for multi-line input
        if (!isSingleLine(trimmed)) return null;

        // Level 1: Most specific patterns (exact format matches)

        // INPUT_ID - Very specific 32-char hex pattern
        if (INPUT_ID_REGEX.matcher(trimmed).matches()) {
            return SearchType.INPUT_ID;
        }

        // ENSEMBL - Specific prefix + number pattern
        if (ENSEMBL_REGEX.matcher(trimmed).matches()) {
            return SearchType.ENSEMBL;
        }

        // PDB - Very specific 4-char pattern
        if (PDB_REGEX.matcher(trimmed).matches()) {
            return SearchType.PDB;
        }

        // UNIPROT - Specific accession format
        if (UNIPROT_REGEX.matcher(trimmed).matches()) {
            return SearchType.UNIPROT;
        }

        // Level 2: Check for VARIANT patterns (aligned with VariantParser logic)

        // Check if it looks like a variant using VariantParser's logic
        VariantInput parsed = VariantParser.parse(trimmed);
        if (parsed != null && parsed.getFormat() != VariantFormat.INVALID) {
                return SearchType.VARIANT;
        }

        // Level 3: Check standalone RefSeq IDs (not part of HGVS)
        // Only match if it doesn't contain HGVS scheme indicators
        if (REFSEQ_REGEX.matcher(trimmed).matches()) {
            return SearchType.REFSEQ;
        }

        // Level 4: GENE - Most permissive, check last to avoid false positives
        if (GENE_REGEX.matcher(trimmed).matches() && !VARIANT_PREFIX_REGEX.matcher(trimmed).matches()) {
            return SearchType.GENE;
        }

        return null;
    }

    public static boolean isSingleLine(String term) {
        return term != null && !term.contains("\n") && !term.contains("\r");
    }
}