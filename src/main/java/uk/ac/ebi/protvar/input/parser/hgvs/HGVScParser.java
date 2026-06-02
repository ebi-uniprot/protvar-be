package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.HGVSCodingInput;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Refer to HGVS doc: https://varnomen.hgvs.org/bg-material/simple/
 * mRNA
 */
public class HGVScParser extends VariantParser {
    // mRNA, Protein-coding transcripts (usually curated) e.g. NM_001145445.1
    public static final String SCHEME_REGEX = ":(\\s*)c\\.";

    // Structural pattern - quick check for general HGVS coding structure: _:c._
    public static final String STRUCTURE_REGEX = String.format("^(?<refseq>[^:]+)%s(?<varDesc>.+)$",
            SCHEME_REGEX // Scheme: :(optional space)c.
    );

    // no anchors (^...$)
    public static final String REFSEQ_REGEX = "(NM_|NP_)\\d+(\\.\\d+)?(\\s*\\([A-Z][A-Z0-9]+\\))?"; // RefSeq part: NM_/NP_ + digits + version + optional gene

    // Basic variant description without special positions
    public static final String VARDESC_REGEX = String.format("(?<pos>%s)(?<refBase>%s)>(?<altBase>%s)" +
                    // Optional protein annotation
                    "(\\s*" +               // Optional space before
                    "(p\\.\\(|\\(p\\.)" +   // Flexible bracket: p.( or (p.
                    "(?<refAA>%s)" +        // REF AA: three letter
                    "(?<aaPos>%s)" +        // AA position
                    "(?<altAA>%s|=|\\*)" +  // ALT AA: three letter, =, or *
                    "\\))?",                // Closing bracket
            VALID_POSITION, VALID_BASE, VALID_BASE,
            ProteinParser.VALID_AA_THREE,
            VALID_POSITION,
            ProteinParser.VALID_AA_THREE
    );

    // Full pattern - for matchesPattern() method
    public static final String FULL_REGEX = String.format("^%s%s%s$",
            REFSEQ_REGEX,
            SCHEME_REGEX,
            VARDESC_REGEX
    );

    private static Pattern STRUCTURE_PATTERN = Pattern.compile(STRUCTURE_REGEX, Pattern.CASE_INSENSITIVE);
    private static Pattern REFSEQ_PATTERN = Pattern.compile("^" + REFSEQ_REGEX + "$", Pattern.CASE_INSENSITIVE);
    private static Pattern VARDESC_PATTERN = Pattern.compile("^" + VARDESC_REGEX + "$", Pattern.CASE_INSENSITIVE);
    private static Pattern FULL_PATTERN = Pattern.compile(FULL_REGEX, Pattern.CASE_INSENSITIVE);

    // Special position patterns for better error messages
    private static final String REGEX_5_PRIME_UTR = String.format("-(?<pos>%s)(?<refBase>%s)>(?<altBase>%s)", VALID_POSITION, VALID_BASE, VALID_BASE);
    private static final String REGEX_INTRON_5_SIDE = String.format("(?<pos>%s)\\+(?<offset>[1-9]\\d*)(?<refBase>%s)>(?<altBase>%s)", VALID_POSITION, VALID_BASE, VALID_BASE);
    private static final String REGEX_INTRON_3_SIDE = String.format("(?<pos>%s)-(?<offset>[1-9]\\d*)(?<refBase>%s)>(?<altBase>%s)", VALID_POSITION, VALID_BASE, VALID_BASE);
    private static final String REGEX_3_PRIME_UTR = String.format("\\*(?<pos>%s)(?<refBase>%s)>(?<altBase>%s)", VALID_POSITION, VALID_BASE, VALID_BASE);

    // Special position patterns
    private static final Pattern PATTERN_5_PRIME_UTR = Pattern.compile("^" + REGEX_5_PRIME_UTR + "$");
    private static final Pattern PATTERN_INTRON_5_SIDE = Pattern.compile("^" + REGEX_INTRON_5_SIDE + "$");
    private static final Pattern PATTERN_INTRON_3_SIDE = Pattern.compile("^" + REGEX_INTRON_3_SIDE + "$");
    private static final Pattern PATTERN_3_PRIME_UTR = Pattern.compile("^" + REGEX_3_PRIME_UTR + "$");

    /**
     * Quick structural check - verifies input looks like HGVS coding format.
     * Checks for general structure: RefSeq:c.variant with relaxed spacing.
     * More permissive than full pattern validation.
     */
    public static boolean matchesStructure(String inputStr) {
        if (inputStr == null || inputStr.trim().isEmpty()) {
            return false;
        }
        return STRUCTURE_PATTERN.matcher(inputStr).lookingAt();
    }

    /**
     * Full pattern validation - validates complete HGVS coding format.
     * Validates RefSeq format, scheme, and variant description syntax.
     */
    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    public static HGVSCodingInput parse(String inputStr) {
        HGVSCodingInput parsedInput = new HGVSCodingInput(inputStr);

        try {
            Matcher structureMatcher = STRUCTURE_PATTERN.matcher(inputStr);
            if (structureMatcher.matches()) {
                String refseqPart = structureMatcher.group("refseq");
                if (REFSEQ_PATTERN.matcher(refseqPart).matches()) {
                    // Extract RefSeq ID and optional gene
                    Matcher refseqMatcher = REFSEQ_PATTERN.matcher(refseqPart);
                    if (refseqMatcher.matches()) {
                        // Extract the actual RefSeq ID (before any gene symbol)
                        String refseqOnly = refseqPart.replaceAll("\\s*\\([A-Z][A-Z0-9]+\\)", "");
                        parsedInput.setRefseqId(refseqOnly);

                        // Extract gene symbol if present
                        Pattern genePattern = Pattern.compile("\\(([A-Z][A-Z0-9]+)\\)");
                        Matcher geneMatcher = genePattern.matcher(refseqPart);
                        if (geneMatcher.find()) {
                            parsedInput.setGeneSymbol(geneMatcher.group(1));
                        }
                    }
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_C_REFSEQ_INVALID);
                }

                String vardescPart = structureMatcher.group("varDesc");
                Matcher vardescMatcher = VARDESC_PATTERN.matcher(vardescPart);

                if (vardescMatcher.matches()) {
                    String pos = vardescMatcher.group("pos");
                    String refBase = vardescMatcher.group("refBase");
                    String altBase = vardescMatcher.group("altBase");

                    parsedInput.setPosition(Integer.parseInt(pos));
                    parsedInput.setRefBase(refBase);
                    parsedInput.setAltBase(altBase);

                    // Optional protein annotation
                    String refAA = vardescMatcher.group("refAA");
                    String altAA = vardescMatcher.group("altAA");
                    String aaPos = vardescMatcher.group("aaPos");

                    if (altAA != null && altAA.equals("=")) {
                        altAA = refAA;
                    }

                    parsedInput.setRefAA(refAA);
                    parsedInput.setAltAA(altAA);
                    parsedInput.setAaPos(aaPos == null ? null : Integer.parseInt(aaPos));
                } else {
                    // Check for specific unsupported position types
                    if (PATTERN_5_PRIME_UTR.matcher(vardescPart).matches()) {
                        parsedInput.addError("5' UTR positions are not supported (e.g., c.-128A>G).");
                    } else if (PATTERN_INTRON_5_SIDE.matcher(vardescPart).matches()) {
                        parsedInput.addError("Intronic positions (5' side) are not supported (e.g., c.128+1G>A).");
                    } else if (PATTERN_INTRON_3_SIDE.matcher(vardescPart).matches()) {
                        parsedInput.addError("Intronic positions (3' side) are not supported (e.g., c.128-1G>A).");
                    } else if (PATTERN_3_PRIME_UTR.matcher(vardescPart).matches()) {
                        parsedInput.addError("3' UTR positions are not supported (e.g., c.*128A>G).");
                    } else {
                        parsedInput.addError(ErrorConstants.HGVS_C_VARDESC_INVALID);
                    }
                }
            } else {
                parsedInput.addError("HGVS c. invalid"); // todo: ErrorConstants.HGVS_C_FORMAT_INVALID
            }
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        }
        return parsedInput;
    }
}
