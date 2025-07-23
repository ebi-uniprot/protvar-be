package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.HGVSCodingInput;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refer to HGVS doc: https://varnomen.hgvs.org/bg-material/simple/
 * mRNA
 */
public class HGVScParser extends InputParser {
    // mRNA, Protein-coding transcripts (usually curated) e.g. NM_001145445.1
    public static final String PREFIX = "NM_"; // not necessary any more!
    public static final String SCHEME = "c.";
    public static final String GENE = "[A-Z][A-Z0-9]+"; // MAIN CRITERIA: must only contain uppercase letters and numbers, start with a letter
    public static final String SCHEME_PATTERN_REGEX = ":(\\s+)?c\\."; // relaxed ":c." or ": c."

    public static final String GENERAL_HGVS_C_PATTERN_REGEX = "^(?<refseq>[^:]+)"+SCHEME_PATTERN_REGEX+"(?<varDesc>[^:]+)$";

    private static final String REF_SEQ_REGEX =
            //"(?<refseqId>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")" + // RefSeq NM accession
            "(?<refseqId>(NM_|NP_)" + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")" +
                    // OPTIONALLY, gene name
                    "(("+ RegexUtils.SPACES +")?\\((?<gene>"+GENE+")\\))?";

    private static final String VAR_DESC_REGEX_ =
            "(?<refBase>" + BASE + ")" +
                    "(" + HGVS.SUB_SIGN + ")" +
                    "(?<altBase>" + BASE + ")" +
                    // OPTIONALLY, with or without space followed by protein substitution
                    "(("+ RegexUtils.SPACES +")?" +
                    "(p.\\(|\\(p.)" + // lenient on where the opening bracket is
                    "(?<refAA>"+ ProteinParser.AMINO_ACID_REF3 + ")" +
                    "(?<aaPos>" + POS + ")" +
                    "(?<altAA>" + ProteinParser.AMINO_ACID_ALT3 +")\\))?";

    private static final String VAR_DESC_REGEX =
            "(?<pos>" + POS + ")" + VAR_DESC_REGEX_;
    private static Pattern GENERAL_PATTERN = Pattern.compile(GENERAL_HGVS_C_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REF_SEQ_PATTERN = Pattern.compile(REF_SEQ_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern VAR_DESC_PATTERN = Pattern.compile(VAR_DESC_REGEX, Pattern.CASE_INSENSITIVE);

    private static final String REGEX_5_PRIME_UTR = "-(?<pos>" + POS + ")";  // 5' UTR
    private static final String REGEX_INTRON_5_SIDE = "(?<pos>" + POS + ")\\+(?<offset>[1-9]\\d*)"; // Intron, 5' side
    private static final String REGEX_INTRON_3_SIDE = "(?<pos>" + POS + ")-(?<offset>[1-9]\\d*)";  // Intron, 3' side
    private static final String REGEX_3_PRIME_UTR = "\\*(?<pos>" + POS + ")"; // 3' UTR

    private static final Pattern PATTERN_5_PRIME_UTR = Pattern.compile(REGEX_5_PRIME_UTR + VAR_DESC_REGEX_);
    private static final Pattern PATTERN_INTRON_5_SIDE = Pattern.compile(REGEX_INTRON_5_SIDE + VAR_DESC_REGEX_);
    private static final Pattern PATTERN_INTRON_3_SIDE = Pattern.compile(REGEX_INTRON_3_SIDE + VAR_DESC_REGEX_);
    private static final Pattern PATTERN_3_PRIME_UTR = Pattern.compile(REGEX_3_PRIME_UTR + VAR_DESC_REGEX_);

    // Pre-check (level 1)
    // Pattern: NM_?:c.?
    public static boolean preCheck(String inputStr) {
        return HGVS.preCheck(PREFIX, SCHEME, inputStr);
    }

    public static boolean startsWithPrefix(String inputStr) {
        return HGVS.startsWithPrefix(PREFIX, inputStr);
    }

    public static boolean matchesPattern(String input) {
        return input.matches(GENERAL_HGVS_C_PATTERN_REGEX);
    }

    public static HGVSCodingInput parse(String inputStr) {
        // pre-condition: fits _:(S?)c._ pattern
        HGVSCodingInput parsedInput = new HGVSCodingInput(inputStr);
        try {
            Matcher generalMatcher = GENERAL_PATTERN.matcher(inputStr);
            if (generalMatcher.matches()) {
                String refseq = generalMatcher.group("refseq");

                Matcher refseqMatcher = REF_SEQ_PATTERN.matcher(refseq);
                if (refseqMatcher.matches()) {
                    String refseqId = refseqMatcher.group("refseqId");
                    parsedInput.setRefseqId(refseqId);
                    // optional gene
                    String gene = refseqMatcher.group("gene");
                    parsedInput.setGeneSymbol(gene);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_C_REFSEQ_INVALID);
                }

                String varDesc = generalMatcher.group("varDesc");
                Matcher varDescMatcher = VAR_DESC_PATTERN.matcher(varDesc);
                if (varDescMatcher.matches()) {
                    String pos = varDescMatcher.group("pos");
                    String refBase = varDescMatcher.group("refBase");
                    String altBase = varDescMatcher.group("altBase");

                    parsedInput.setPosition(Integer.parseInt(pos));
                    parsedInput.setRefBase(refBase);
                    parsedInput.setAltBase(altBase);

                    // Optional fields
                    String refAA = varDescMatcher.group("refAA");
                    String altAA = varDescMatcher.group("altAA");
                    if (altAA != null && altAA.equals("=")) {
                        altAA = refAA;
                    }
                    String aaPos = varDescMatcher.group("aaPos");

                    parsedInput.setRefAA(refAA);
                    parsedInput.setAltAA(altAA);
                    parsedInput.setAaPos(aaPos == null ? null : Integer.parseInt(aaPos));

                } else {
                    if (PATTERN_5_PRIME_UTR.matcher(varDesc).matches()) {
                        parsedInput.addError("5' UTR positions are not supported (e.g., c.-128A>G).");
                    } else if (PATTERN_INTRON_5_SIDE.matcher(varDesc).matches()) {
                        parsedInput.addError("Intronic positions (5' side) are not supported (e.g., c.128+1G>A).");
                    } else if (PATTERN_INTRON_3_SIDE.matcher(varDesc).matches()) {
                        parsedInput.addError("Intronic positions (3' side) are not supported (e.g., c.128-1G>A).");
                    } else if (PATTERN_3_PRIME_UTR.matcher(varDesc).matches()) {
                        parsedInput.addError("3' UTR positions are not supported (e.g., c.*128A>G).");
                    } else {
                        parsedInput.addError(ErrorConstants.HGVS_C_VARDESC_INVALID);
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

}
