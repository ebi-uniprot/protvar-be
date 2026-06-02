package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.VariantInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refer to HGVS doc: https://varnomen.hgvs.org/bg-material/simple/
 * HGVS format
 * - complete variant description "reference:description"
 * e.g.
 *     * NM_004006.2:c.4375C>T
 *     * NC_000023.11:g.32389644G>A
 *
 * All variants are described in relation to a reference, so called "reference sequence".
 * After the reference, a description of the variant follows.
 *
 * # Reference sequences
 * e.g.
 * * genomic (nucleotide)
 *   - NC_ a genomic reference sequence based on a chromosome
 *     NC_000023.9:g.32317682G>A (Mar.2006: hg18, NCBI36)
 *     NC_000023.10:g.32407761G>A (Feb.2009: h19, GRCh37)
 *     NC_000023.11:g.32389644G>A (Dec.2013: hg38, GRCh38)
 *   - NG_ a genomic reference sequence based on a Gene or Genomic region
 *     NG_012232.1:g.954966C>T
 *   - LRG_ a genomic reference sequence, used in a diagnostic setting, based on a Gene or Genomic region
 *     LRG_199:g.954966C>T
 * * transcript (RNA, nucleotide)
 *   - NM_ a reference sequence based on a protein coding RNA (mRNA)
 *     NM_004006.2:c.4375C>T
 *   - NR_ a reference sequence based on a non-protein coding RNA
 *     NR_002196.1:n.601G>T
 * * protein (amino acid)
 *   - NP_ a reference sequence based on a protein (amino acid) sequence
 *     NP_003997.1:p.Arg1459* (p.Arg1459Ter)
 *
 * Variants
 * A standard variant description has the format "prefix.position(s)_change".
 *
 *
 * Supported RefSeq accession prefixes for HGVS inputs
 * Source: The NCBI Handbook.
 *
 */
public class HGVS {

    // Generic HGVS structure pattern for initial validation
    public static final String GENERAL_HGVS_PATTERN_REGEX = "^(?<refseqId>[^:]+):(?<scheme>(\\s+)?[a-z]\\.)[^:]+$";
    private static final Pattern GENERAL_HGVS_PATTERN = Pattern.compile(GENERAL_HGVS_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check for any HGVS format
     */
    public static boolean matchesStructure(String input) {
        return input != null && GENERAL_HGVS_PATTERN.matcher(input).matches();
    }

    /**
     * Create invalid input with appropriate error messages for unsupported formats
     */
    public static VariantInput invalid(String inputStr) {
        VariantInput invalid = new VariantInput(VariantFormat.INVALID, inputStr);
        Matcher generalMatcher = GENERAL_HGVS_PATTERN.matcher(inputStr);
        if (generalMatcher.matches()) {
            String scheme = generalMatcher.group("scheme");
            scheme = scheme == null ? "" : scheme.trim();

            switch (scheme) {
                case "n." -> invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_SCHEME_N);
                case "m." -> invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_SCHEME_M);
                case "r." -> invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_SCHEME_R);
                default -> invalid.addError(ErrorConstants.HGVS_INVALID_SCHEME);
            }

            String refseqId = generalMatcher.group("refseqId");
            refseqId = refseqId == null ? "" : refseqId.trim().toUpperCase();

            if (refseqId.startsWith("NG"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX_NG);
            else if (refseqId.startsWith("LRG"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX_LRG);
            else if (refseqId.startsWith("NR"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX_NR);
            else if (!refseqId.matches("(NC_|NM_|NP_)\\d+(\\.\\d+)?"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX);
        }

        if (!invalid.hasError())
            invalid.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        return invalid;
    }
}
