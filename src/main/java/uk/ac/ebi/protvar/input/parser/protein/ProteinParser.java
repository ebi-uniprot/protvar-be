package uk.ac.ebi.protvar.input.parser.protein;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.parser.VariantParser;
import uk.ac.ebi.protvar.input.ProteinInput;
import uk.ac.ebi.protvar.types.AminoAcid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ProteinParser for internal protein variant formats
 *
 * Supported formats:
 * 1. ACC POS            --> Position only (no AA substitution)
 * 2. ACC POS X[ |/]Y    --> Single letter amino acids with space or slash separator
 * 3. ACC POS XXX[ |/]YYY --> Three letter amino acids with space or slash separator
 * 4. ACC X999Y          --> Compact single letter format
 * 5. ACC [p.]XXX999YYY  --> Compact three letter format (HGVS-like with optional p. prefix)
 *
 * Examples:
 * - P22304 205          (position only)
 * - P22304 205 A P      (single letter with space)
 * - P22304 205 A/P      (single letter with slash)
 * - P07949 783 Asn Thr  (three letter with space)
 * - P22304 A205P        (compact single letter)
 * - P07949 p.Asn783Thr (compact three letter with p. prefix)
 *
 * Notes:
 * - Just ACC alone is not supported (would be identifier, not variant)
 * - UniProt accession pattern validates format but doesn't guarantee existence
 * - "=" notation represents unchanged/silent mutations
 * - "*" represents stop codon (Ter in three-letter format)
 */
public class ProteinParser extends VariantParser {
    // UniProt accession pattern - validates format structure only
    public static final String VALID_UNIPROT = "([OPQ][0-9][A-Z0-9]{3}[0-9]|[A-NR-Z]([0-9][A-Z][A-Z0-9]{2}){1,2}[0-9])(-[1-9][0-9]*)?";

    // Amino acid patterns derived from AminoAcid enum
    public static final String VALID_AA_SINGLE = "[ACDEFGHIKLMNPQRSTVWY\\*BZUXOJ]";  // Single letter codes + stop codon (*)
    public static final String VALID_AA_THREE = "(Ala|Cys|Asp|Glu|Phe|Gly|His|Ile|Lys|Leu|Met|Asn|Pro|Gln|Arg|Ser|Thr|Val|Trp|Tyr|Ter|Asx|Glx|Sec|Unk|Pyl|Xle)";

    // Structural pattern - verifies UniProt accession followed by content
    public static final Pattern PROTEIN_STRUCTURE = Pattern.compile("^(" + VALID_UNIPROT + ")\\s+", Pattern.CASE_INSENSITIVE);

    // Full validation patterns with named groups for easier parsing

    // Spaced formats: ACC POS [REF [ALT]]
    // - REF and ALT are optional (supports position-only queries)
    // - ALT can be separated by space or slash (/)
    // - ALT can be "=" for unchanged/silent mutations

    // Format 1-2: ACC POS [REF [ALT]] - single letter amino acids
    public static final String PATTERN_ACC_POS_SINGLE = String.format(
            "^(?<acc>%s)\\s+(?<pos>%s)(?:\\s+(?<ref>%s)(?:[\\s/](?<alt>%s|=))?)?$",
            VALID_UNIPROT, VALID_POSITION, VALID_AA_SINGLE, VALID_AA_SINGLE);

    // Format 3: ACC POS [REF [ALT]] - three letter amino acids
    // Note: ALT can also be "*" (stop codon) in addition to three-letter codes
    public static final String PATTERN_ACC_POS_THREE = String.format(
            "^(?<acc>%s)\\s+(?<pos>%s)(?:\\s+(?<ref>%s)(?:[\\s/](?<alt>%s|=|\\*))?)?$",
            VALID_UNIPROT, VALID_POSITION, VALID_AA_THREE, VALID_AA_THREE);

    // Compact formats: ACC [p.]REF POS ALT
    // - All components (REF, POS, ALT) are required
    // - Optional "p." prefix for HGVS-like notation
    // - ALT can be "=" for unchanged/silent mutations

    // Format 4: ACC [p.]REF POS ALT - compact single letter
    public static final String PATTERN_COMPACT_SINGLE = String.format(
            "^(?<acc>%s)\\s+(?:p\\.)?(?<ref>%s)(?<pos>%s)(?<alt>%s|=)$",
            VALID_UNIPROT, VALID_AA_SINGLE, VALID_POSITION, VALID_AA_SINGLE);

    // Format 5: ACC [p.]REF POS ALT - compact three letter
    // Note: ALT can also be "*" (stop codon) in addition to three-letter codes
    public static final String PATTERN_COMPACT_THREE = String.format(
            "^(?<acc>%s)\\s+(?:p\\.)?(?<ref>%s)(?<pos>%s)(?<alt>%s|=|\\*)$",
            VALID_UNIPROT, VALID_AA_THREE, VALID_POSITION, VALID_AA_THREE);

    // Compiled patterns
    public static final Pattern ACC_PATTERN = Pattern.compile("^" + VALID_UNIPROT + "$", Pattern.CASE_INSENSITIVE);
    public static final Pattern ACC_POS_SINGLE_PATTERN = Pattern.compile(PATTERN_ACC_POS_SINGLE, Pattern.CASE_INSENSITIVE);
    public static final Pattern ACC_POS_THREE_PATTERN = Pattern.compile(PATTERN_ACC_POS_THREE, Pattern.CASE_INSENSITIVE);
    public static final Pattern COMPACT_SINGLE_PATTERN = Pattern.compile(PATTERN_COMPACT_SINGLE, Pattern.CASE_INSENSITIVE);
    public static final Pattern COMPACT_THREE_PATTERN = Pattern.compile(PATTERN_COMPACT_THREE, Pattern.CASE_INSENSITIVE);


    /**
     * Quick structural check - verifies input looks like a protein variant.
     * Checks that input starts with valid UniProt accession followed by additional content.
     *
     * @param input Input string to validate
     * @return true if input has protein variant structure, false otherwise
     */
    public static boolean matchesStructure(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        // Ensure we have accession + at least one more component
        String[] parts = input.trim().split("\\s+", 2);
        return parts.length >= 2 && validAccession(parts[0]);
    }

    /**
     * Full pattern validation - tests input against all supported protein formats.
     * Only called after structural validation passes.
     *
     * @param input Input string to validate
     * @return true if input matches any supported protein format, false otherwise
     */
    public static boolean matchesPattern(String input) {
        if (!matchesStructure(input)) {
            return false;
        }

        return ACC_POS_SINGLE_PATTERN.matcher(input).matches() ||
                ACC_POS_THREE_PATTERN.matcher(input).matches() ||
                COMPACT_SINGLE_PATTERN.matcher(input).matches() ||
                COMPACT_THREE_PATTERN.matcher(input).matches();
    }

    /**
     * Validates UniProt accession format.
     * Note: This only validates the format structure, not whether the accession exists.
     *
     * @param acc Accession string to validate
     * @return true if accession has valid UniProt format, false otherwise
     */
    public static boolean validAccession(String acc) {
        return acc != null && ACC_PATTERN.matcher(acc).matches();
    }

    /**
     * Parses protein variant input using two-level validation approach.
     * First performs quick structural check, then attempts full parsing.
     *
     * @param inputStr Input string to parse
     * @return ProteinInput object with parsed data or error information
     */
    public static ProteinInput parse(String inputStr) {
        // Level 1: Quick structural validation
        if (!matchesStructure(inputStr)) {
            ProteinInput invalid = new ProteinInput(inputStr);
            invalid.addError(ErrorConstants.INVALID_PROTEIN_INPUT);
            return invalid;
        }

        ProteinInput parsedInput = new ProteinInput(inputStr);

        try {
            // Level 2: Try patterns in order of specificity (most specific first)
            if (tryParse(COMPACT_SINGLE_PATTERN, inputStr, parsedInput)) return parsedInput;
            if (tryParse(COMPACT_THREE_PATTERN, inputStr, parsedInput)) return parsedInput;
            if (tryParse(ACC_POS_SINGLE_PATTERN, inputStr, parsedInput)) return parsedInput;
            if (tryParse(ACC_POS_THREE_PATTERN, inputStr, parsedInput)) return parsedInput;

            // Structure matched but no specific pattern matched
            parsedInput.addError(ErrorConstants.INVALID_PROTEIN_INPUT);

        } catch (Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_PROTEIN_INPUT);
            LOGGER.error("Protein parsing error for: " + inputStr, ex);
        }

        return parsedInput;
    }

    /**
     * Attempts to parse input using a specific pattern with named groups.
     * All patterns use consistent group names: acc, pos, ref, alt.
     *
     * @param pattern Compiled regex pattern to try
     * @param inputStr Input string to parse
     * @param parsedInput ProteinInput object to populate
     * @return true if parsing succeeded, false otherwise
     */
    private static boolean tryParse(Pattern pattern, String inputStr, ProteinInput parsedInput) {
        Matcher matcher = pattern.matcher(inputStr);
        if (!matcher.matches())
            return false;

        try {
            // All patterns have these named groups
            String acc = matcher.group("acc");  // Always present
            String pos = matcher.group("pos");  // Always present
            String ref = matcher.group("ref");  // May be null for position-only
            String alt = matcher.group("alt");  // May be null

            parsedInput.setAccession(acc.toUpperCase());
            parsedInput.setPosition(Integer.parseInt(pos));

            // Set optional amino acid fields
            if (ref != null) {
                parsedInput.setRefAA(normalizeAA(ref));

                if (alt != null) {
                    parsedInput.setAltAA(normalizeAA(normalizeEquals(alt, ref)));
                }
            }

            return true;

        } catch (Exception e) {
            LOGGER.warn("Failed to parse protein input: {}", inputStr, e);
            return false;
        }
    }

    /**
     * Normalizes amino acid to single letter code
     */
    public static String normalizeAA(String aa) {
        if (aa == null) return null;

        for (AminoAcid value : AminoAcid.values()) {
            if (value.getOneLetter().equalsIgnoreCase(aa)
                    || value.getThreeLetter().equalsIgnoreCase(aa))
                return value.getOneLetter();
        }
        return null;
    }

    /**
     * Handles "=" (unchanged) notation
     */
    public static String normalizeEquals(String alt, String ref) {
        return (alt != null && alt.equals("=")) ? ref : alt;
    }
}
