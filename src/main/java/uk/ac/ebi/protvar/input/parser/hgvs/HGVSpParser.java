package uk.ac.ebi.protvar.input.parser.hgvs;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.VariantFormat;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.parser.protein.ProteinParser;
import uk.ac.ebi.protvar.input.ProteinInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supported HGVS protein formats (flexible approach - mixed amino acid notation allowed):
 * - NP_xxx.x:p.Arg490Ser     (three letter REF and ALT)
 * - NP_xxx.x:p.R490S         (single letter REF and ALT)
 * - NP_xxx.x:p.R490Ser       (single letter REF, three letter ALT)
 * - NP_xxx.x:p.Arg490*       (three letter REF, single letter stop)
 * - NP_xxx.x:p.R490*         (single letter REF, stop codon)
 * - NP_xxx.x:p.R490Ter       (single letter REF, three letter stop)
 * - NP_xxx.x:p.(Arg490Ser)   (optional parentheses around variant)
 * - NP_xxx.x:p.R490=         (unchanged notation)
 * - NP_xxx.x: p.R490S        (relaxed spacing - space between : and p.)
 *
 * Notes:
 * - RefSeq ID can be NP_ (protein) or NM_ (transcript)
 * - Mixed amino acid notation is allowed (e.g., R490Ser, Arg490*)
 * - Parentheses around variant description are optional
 * - Relaxed spacing between : and p. is supported
 * - All amino acids are normalized to single letter codes in output
 * - "=" represents unchanged/silent mutations
 * - "*" and "Ter" both represent stop codon
 */
public class HGVSpParser extends VariantParser {
    // Protein, Associated with an NM_ or NC_ accession e.g. NP_001138917.1
    public static final String SCHEME_REGEX = ":(\\s*)p\\.";

    // Structural pattern - quick check for general HGVS protein structure: _:p._
    // Relaxed to allow space between : and p. (e.g., "NP_123: p.R490S")
    public static final String STRUCTURE_REGEX = String.format("^(?<refseq>[^:]+)%s(?<varDesc>.+)$",
            SCHEME_REGEX // Scheme: :(optional space)p.
    );

    // no anchors (^...$)
    public static final String REFSEQ_REGEX = "(NM_|NP_)\\d+(\\.\\d+)?"; // RefSeq part: NM_/NP_ + digits + version

    // no anchors (^...$)
    public static final String VARDESC_REGEX = String.format("\\(?(?<ref>%s|%s)(?<pos>%s)(?<alt>%s|%s|=)\\)?",
            ProteinParser.VALID_AA_SINGLE, ProteinParser.VALID_AA_THREE,  // REF: single OR three letter
            VALID_POSITION,                       // Position: digits
            ProteinParser.VALID_AA_SINGLE, ProteinParser.VALID_AA_THREE   // ALT: single OR three letter (includes * and Ter)
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

    /**
     * Quick structural check - verifies input looks like HGVS protein format.
     * Checks for general structure: RefSeq:p.variant with relaxed spacing.
     * More permissive than full pattern validation.
     */
    public static boolean matchesStructure(String inputStr) {
        if (inputStr == null || inputStr.trim().isEmpty()) {
            return false;
        }
        return STRUCTURE_PATTERN.matcher(inputStr).lookingAt();
    }

    /**
     * Full pattern validation - validates complete HGVS protein format.
     * Validates RefSeq format, scheme, and variant description syntax.
     */
    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    public static ProteinInput parse(String inputStr) {
        ProteinInput parsedInput = new ProteinInput(inputStr);
        parsedInput.setFormat(VariantFormat.HGVS_PROTEIN);

        try {
            Matcher structureMatcher = STRUCTURE_PATTERN.matcher(inputStr);
            if (structureMatcher.matches()) {
                String refseqPart = structureMatcher.group("refseq");
                if (REFSEQ_PATTERN.matcher(refseqPart).matches()) {
                    parsedInput.setRefseqId(refseqPart);
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_P_REFSEQ_INVALID);
                }

                String vardescPart = structureMatcher.group("varDesc");
                Matcher vardescMatcher = VARDESC_PATTERN.matcher(vardescPart);

                if (vardescMatcher.matches()) {
                    String pos = vardescMatcher.group("pos");
                    String ref = vardescMatcher.group("ref");
                    String alt = vardescMatcher.group("alt");

                    alt = ProteinParser.normalizeEquals(alt, ref);
                    parsedInput.setPosition(Integer.parseInt(pos));
                    parsedInput.setRefAA(ProteinParser.normalizeAA(ref));
                    parsedInput.setAltAA(ProteinParser.normalizeAA(alt));
                } else {
                    parsedInput.addError(ErrorConstants.HGVS_P_VARDESC_INVALID);
                }
            } else {
                parsedInput.addError("HGVS p. invalid"); // todo: ErrorConstants.HGVS_P_FORMAT_INVALID
            }
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addError(ErrorConstants.HGVS_GENERIC_ERROR);
        }
        return parsedInput;
    }
}
