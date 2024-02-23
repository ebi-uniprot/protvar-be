package uk.ac.ebi.protvar.utils;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.format.genomic.HGVSg;

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

    public static final String HGVS_SUPPORTED_PREFIXES = "(NC_|NM_|NP_)"; // not support NG_|LRG_|NR_
    public static final String HGVS_SUPPORTED_SCHEMES = "(g|c|p)"; // not supported n|m|r

    public static final String POSTFIX_NUM = "(\\d+)";
    public static final String VERSION_NUM = "(\\.\\d+)?"; // optional

    public static final String HGVS_SUPPORTED_REFERENCE = HGVS_SUPPORTED_PREFIXES + POSTFIX_NUM + VERSION_NUM;

    public static final String COLON = ":";
    public static final String DOT = "\\.";
    public static final String SUB_SIGN = ">";

    // letters, numbers, underscores, dots, any order.
    public static final String GENERIC_HGVS_ACCESSION = "([A-Za-z0-9\\.\\_]+)";
    public static final String GENERIC_BASE = "[a-zA-Z\\*]";

    // any letter, number, dot, brackets, star, greater than sign
    public static final String SUB_PART = "[0-9a-zA-Z\\.\\(\\)\\*\\>]{3,}";
    // g. <POS><A>\\><A>
    // c. <POS><A>\\><A> optionally, (p.<AAA><POS><AAA>)
    // p. <AAA><POS><AAA>| <AAA><POS>\\* | <A><POS><A> | <A><POS>\\*

    public static final String GENERAL_HGVS_REF_SEQ_AND_VAR_DESC_FORMAT = "^[^:]+:[^:]+$";
    public static final String GENERAL_HGVS_REF_SEQ_FORMAT = "^[^ :]+$";
    public static final String GENERAL_HGVS_VAR_DESC_FORMAT = "^[^ :]+$";

    public static boolean generalRefSeqVarDesc(String input) {
        return input.matches(GENERAL_HGVS_REF_SEQ_AND_VAR_DESC_FORMAT);
    }


    public static boolean startsWithPrefix(String prefix, String inputStr) {
        return inputStr.toUpperCase().startsWith(prefix);
    }

    public static HGVSg invalid(String inputStr) {
        HGVSg invalid = new HGVSg(inputStr);
        if (!HGVS.supportedPrefix(inputStr)) {
            if (inputStr.toUpperCase().startsWith("NG"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX_NG);
            else if (inputStr.toUpperCase().startsWith("LRG"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX_LRG);
            else if (inputStr.toUpperCase().startsWith("NR"))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX_NR);
            else
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_PREFIX);
        }
        if (!HGVS.containsSupportedScheme(inputStr)) {
            if (inputStr.contains(":n."))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_SCHEME_N);
            else if (inputStr.contains(":m."))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_SCHEME_M);
            else if (inputStr.contains(":r."))
                invalid.addError(ErrorConstants.HGVS_UNSUPPORTED_SCHEME_R);
            else
                invalid.addError(ErrorConstants.HGVS_INVALID_SCHEME);
        }
        if (!invalid.hasError())
            invalid.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        return invalid;
    }

    public static boolean preCheck(String prefix, String scheme, String input) {
        return input.toUpperCase().startsWith(prefix) || input.contains(scheme);
    }


    public static boolean supportedPrefix(String input) {
        return RegexUtils.matchIgnoreCase("^"+ HGVS_SUPPORTED_PREFIXES, input);
    }

    public static boolean containsSupportedScheme(String input) {
        return RegexUtils.matchIgnoreCase("^.*:"+ HGVS_SUPPORTED_SCHEMES + ".*$", input);
    }

    public static boolean validRefSeq(String input) {
        return RegexUtils.matchIgnoreCase("^"+ HGVS_SUPPORTED_REFERENCE + "$", input);
    }

}
