package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.VariantParser;

import java.util.regex.Pattern;

/**
 * COSMIC (Catalogue Of Somatic Mutations In Cancer) ID parser
 *
 * Supports:
 *  - New IDs with prefix COSV (e.g. COSV123456)
 *  - Legacy IDs with prefixes COSM, COSN
 *
 * Notes:
 *  - Case-insensitive matching
 *  - IDs consist of prefix + digits (no length limit on digits)
 */
public class CosmicParser extends VariantParser {
    public static final int PREFIX_LEN = 4;

    // COSMIC ID prefixes
    public static final String COSV = "COSV";  // New IDs
    public static final String COSM = "COSM";  // Legacy
    public static final String COSN = "COSN";  // Legacy

    // Structural pattern - quick check for COSMIC-like structure
    static final Pattern STRUCTURE_PATTERN = Pattern.compile("^(COSV|COSM|COSN)\\d", Pattern.CASE_INSENSITIVE);

    // Full validation pattern: prefix followed by digits
    static final Pattern FULL_PATTERN = Pattern.compile("^(COSV|COSM|COSN)\\d+$", Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check - doesn't validate full format
     */
    public static boolean matchesStructure(String input) {
        return input != null && STRUCTURE_PATTERN.matcher(input).lookingAt();
    }

    /**
     * Full pattern validation - validates complete format
     */
    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    /**
     * Parses the input string into a VariantInput, adding an error if invalid.
     */
    public static VariantInput parse(String inputStr) {
        VariantInput parsedInput = new VariantInput(VariantFormat.COSMIC, inputStr);
        if (!matchesPattern(inputStr)) {
            LOGGER.warn("Invalid COSMIC ID format: {}", inputStr);
            parsedInput.addError(ErrorConstants.COSMIC_ID_INVALID);
        }
        return parsedInput;
    }

    /**
     * Extracts the COSMIC prefix from the input, or returns empty string if invalid.
     */
    public static String getPrefix(String inputStr) {
        if (inputStr == null || inputStr.length() < PREFIX_LEN) {
            return "";
        }
        String prefix = inputStr.substring(0, PREFIX_LEN).toUpperCase();
        if (prefix.equals(COSV) || prefix.equals(COSM) || prefix.equals(COSN)) {
            return prefix;
        }
        return "";
    }
}
