package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.GenomicInput;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.input.parser.genomic.GenomicParser;
import uk.ac.ebi.protvar.types.RefseqChr;
import uk.ac.ebi.protvar.utils.HGVS;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HGVSgParser extends InputParser {
    // Genomic, Complete genomic molecule, usually reference assembly
    public static final String PREFIX = "NC_"; // -> converts to chr
    public static final String SCHEME = "g.";

    public static final String SCHEME_PATTERN_REGEX = ":(\\s+)?g\\.";
    public static final String GENERAL_HGVS_G_PATTERN_REGEX = "^(?<refseqId>[^:]+)"+SCHEME_PATTERN_REGEX+"(?<varDesc>[^:]+)$";

    public static final String REF_SEQ_REGEX = "(?<refseqId>"+PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM + ")"; // RefSeq NC accession
    public static final String VAR_DESC_REGEX =
            "(?<pos>"+ POS + ")" +
                    "(?<sub>"+ GenomicParser.BASE_SUB + ")"; // (A|T|C|G)>(A|T|C|G)
    private static Pattern GENERAL_PATTERN = Pattern.compile(GENERAL_HGVS_G_PATTERN_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REF_SEQ_PATTERN = Pattern.compile(REF_SEQ_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern VAR_DESC_PATTERN = Pattern.compile(VAR_DESC_REGEX, Pattern.CASE_INSENSITIVE);

    // Pre-check (level 1) - starts with prefix or contains scheme
    // Pattern: NC_?:g.?
    public static boolean preCheck(String inputStr) {
        return HGVS.preCheck(PREFIX, SCHEME, inputStr);
    }

    public static boolean startsWithPrefix(String inputStr) {
        return HGVS.startsWithPrefix(PREFIX, inputStr);
    }

    public static boolean matchesPattern(String input) {
        return input.matches(GENERAL_HGVS_G_PATTERN_REGEX);
    }

    public static GenomicInput parse(String inputStr) {
        // pre-condition: fits _:(S?)g._ pattern
        GenomicInput parsedInput = new GenomicInput(inputStr);
        parsedInput.setFormat(VariantFormat.HGVS_GENOMIC);
        try {
            Matcher generalMatcher = GENERAL_PATTERN.matcher(inputStr);
            if (generalMatcher.matches()) {
                String refseqId = generalMatcher.group("refseqId");
                if (REF_SEQ_PATTERN.matcher(refseqId).matches()) {
                    for (RefseqChr val : RefseqChr.values()) {
                        if (val.getRefseqId38().equalsIgnoreCase(refseqId)) {
                            parsedInput.setChromosome(val.getChr());
                            break;
                        }
                        if (val.getRefseqId37().equalsIgnoreCase(refseqId)) {
                            parsedInput.setChromosome(val.getChr());
                            parsedInput.setRefseq37(true);
                            break;
                        }
                    }
                    if (parsedInput.getChromosome() == null) {
                        parsedInput.addError(ErrorConstants.HGVS_G_REFSEQ_NOT_MAP_TO_CHR);
                    }
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_G_REFSEQ_INVALID);
                }

                String varDesc = generalMatcher.group("varDesc");
                Matcher varDescMatcher = VAR_DESC_PATTERN.matcher(varDesc);
                if (varDescMatcher.matches()) {
                    String pos = varDescMatcher.group("pos");
                    String sub = varDescMatcher.group("sub");
                    String[] bases = sub.split(HGVS.SUB_SIGN);
                    String ref = bases[0];
                    String alt = bases[1];

                    parsedInput.setPosition(Integer.parseInt(pos));
                    parsedInput.setRefBase(ref);
                    parsedInput.setAltBase(alt);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_G_VARDESC_INVALID);
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

    public static boolean validRefSeq(String val) {
        return RegexUtils.matchIgnoreCase(PREFIX + HGVS.POSTFIX_NUM + HGVS.VERSION_NUM, val);
    }
    public static boolean validRef(String val) {
        return RegexUtils.matchIgnoreCase(BASE, val);
    }
    public static boolean validAlt(String val) {
        return RegexUtils.matchIgnoreCase(BASE, val);
    }

    public static Integer extractLocation(String inputStr) {
        try {
            Matcher generalMatcher = GENERAL_PATTERN.matcher(inputStr);
            if (generalMatcher.matches()) {
                String varDesc = generalMatcher.group("varDesc");
                Matcher varDescMatcher = VAR_DESC_PATTERN.matcher(varDesc);
                if (varDescMatcher.matches()) {
                    String pos = varDescMatcher.group("pos");
                    return Integer.parseInt(pos);
                }
            }
        } catch (Exception ex) {
            // return null
        }
        return null;
    }

}
