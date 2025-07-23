package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;
import uk.ac.ebi.protvar.input.ProteinInput;
import uk.ac.ebi.protvar.utils.HGVS;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valid format:
 * NP_xxx:p.(Arg490Ser)     regex
 * NP_xxx:p.Arg490Ser    -> AA3 + POS + AA3
 * NP_xxx:p.R490S        -> AA1 + POS + AA1
 * NP_xxx:p.Trp87Ter     -> AA3 + POS + AA3
 * NP_xxx:p.Trp78*       -> AA3 + POS + *
 * NP_xxx:p.W87*         -> AA1 + POS + AA1
 */
public class HGVSpParser extends InputParser {
    // Protein, Associated with an NM_ or NC_ accession e.g. NP_001138917.1
    public static final String PREFIX = "NP_"; // -> lookup transcript (enst)? // not necessary any more!
    public static final String SCHEME = "p.";
    public static final String SCHEME_PATTERN_REGEX = ":(\\s+)?p\\.";

    public static final String GENERAL_HGVS_P_PATTERN_REGEX = "^(?<refseqId>[^:]+)"+SCHEME_PATTERN_REGEX+"(?<varDesc>[^:]+)$";

    private static final String REF_SEQ_REGEX =
            //"(?<refseqId>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")"; // RefSeq.NP accession
            "(NM_|NP_)" + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM;

    private static final String VAR_DESC_X_POS_Y = "(\\()?" +
            "(?<ref>"+ ProteinParser.AMINO_ACID_REF1 + ")" +
            "(?<pos>" + POS + ")" +
            "(?<alt>" + ProteinParser.AMINO_ACID_ALT1 + ")" + // includes STOP_CODON (*)
            "(\\))?";
    private static final String VAR_DESC_XXX_POS_YYY = "(\\()?" +
            "(?<ref>"+ ProteinParser.AMINO_ACID_REF3 + ")" +
            "(?<pos>" + POS + ")" +
            "(?<alt>" + ProteinParser.AMINO_ACID_ALT3 + ")" +
            "(\\))?";

    private static Pattern GENERAL_PATTERN = Pattern.compile(GENERAL_HGVS_P_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REF_SEQ_PATTERN = Pattern.compile(REF_SEQ_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern PATTERN_VAR_DESC_X_POS_Y = Pattern.compile(VAR_DESC_X_POS_Y, Pattern.CASE_INSENSITIVE);
    private static Pattern PATTERN_VAR_DESC_XXX_POS_YYY = Pattern.compile(VAR_DESC_XXX_POS_YYY, Pattern.CASE_INSENSITIVE);

    // Pre-check (level 1)
    // Pattern: NP_?:p.?
    public static boolean preCheck(String inputStr) {
        return HGVS.preCheck(PREFIX, SCHEME, inputStr);
    }

    public static boolean startsWithPrefix(String inputStr) {
        return HGVS.startsWithPrefix(PREFIX, inputStr);
    }

    public static boolean matchesPattern(String input) {
        return input.matches(GENERAL_HGVS_P_PATTERN_REGEX);
    }

    public static ProteinInput parse(String inputStr) {
        // pre-condition: fits _:(S?)p._ pattern
        ProteinInput parsedInput = new ProteinInput(inputStr);
        parsedInput.setFormat(VariantFormat.HGVS_PROTEIN);
        try {
            Matcher generalMatcher = GENERAL_PATTERN.matcher(inputStr);
            if (generalMatcher.matches()) {
                String refseqId = generalMatcher.group("refseqId");
                if (REF_SEQ_PATTERN.matcher(refseqId).matches()) {
                    parsedInput.setRefseqId(refseqId);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_P_REFSEQ_INVALID);
                }

                String varDesc = generalMatcher.group("varDesc");
                Matcher varDescMatcher1 = PATTERN_VAR_DESC_X_POS_Y.matcher(varDesc);
                if (varDescMatcher1.matches()) { // 1 letter AA
                    setParams(parsedInput, varDescMatcher1);
                } else {
                    Matcher varDescMatcher2 = PATTERN_VAR_DESC_XXX_POS_YYY.matcher(varDesc);
                    if (varDescMatcher2.matches()) { // 3 letter AA
                        setParams(parsedInput, varDescMatcher2);
                    } else {
                        parsedInput.addError(ErrorConstants.HGVS_P_VARDESC_INVALID);
                    }
                }
            } else {
                throw new InvalidInputException("No match found.");
            }
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        }
        return parsedInput;
    }

    private static void setParams(ProteinInput input, Matcher matcher) {
        String pos = matcher.group("pos");
        String ref = matcher.group("ref");
        String alt = matcher.group("alt");
        alt = ProteinParser.normalizeAltAllele(alt, ref);
        input.setPosition(Integer.parseInt(pos));
        input.setRefAA(ref);
        input.setAltAA(alt);
    }
}
