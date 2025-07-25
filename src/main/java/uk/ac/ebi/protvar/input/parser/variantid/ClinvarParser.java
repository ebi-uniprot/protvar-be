package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.VariantParser;

import java.util.regex.Pattern;

/**
 * ClinVar Accession parser
 *
 * Supports:
 *  - RCV000000000.0 (Reference ClinVar Condition)
 *  - VCV000000000.0 (Variant ClinVar Record)
 *
 * Notes:
 *  - Case-insensitive (e.g. vcv123456789.1 is accepted)
 *  - SCV accessions (submissions) are not supported
 *  - Version suffix (.0, .1, etc.) is optional
 */
public class ClinvarParser extends VariantParser {
    public static final int PREFIX_LEN = 3;
    public static final String RCV = "RCV";
    public static final String VCV = "VCV";

    // Structural pattern - quick check for ClinVar-like structure
    static final Pattern STRUCTURE_PATTERN = Pattern.compile("^(RCV|VCV)\\d", Pattern.CASE_INSENSITIVE);

    // Full validation pattern: 9 digits, optional version
    static final Pattern FULL_PATTERN = Pattern.compile("^(RCV|VCV)\\d{9}(\\.\\d+)?$", Pattern.CASE_INSENSITIVE);

    /**
     * Quick structural check - doesn't validate digit count or version format
     */
    public static boolean matchesStructure(String input) {
        return input != null && STRUCTURE_PATTERN.matcher(input).lookingAt();
    }

    /**
     * Full pattern validation - validates exact format requirements
     */
    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    /**
     * Parses the input string into a VariantInput.
     * Adds an error if the input does not match the expected pattern.
     */
    public static VariantInput parse(String inputStr) {
        VariantInput parsedInput = new VariantInput(VariantFormat.CLINVAR, inputStr);
        if (!matchesPattern(inputStr)) {
            LOGGER.warn("Invalid ClinVar ID format: {}", inputStr);
            parsedInput.addError(ErrorConstants.CLINVAR_ID_INVALID);
        }
        return parsedInput;
    }

    /**
     * Extracts the accession prefix (RCV or VCV), or empty string if invalid.
     */
    public static String getPrefix(String inputStr) {
        if (inputStr == null || inputStr.length() < PREFIX_LEN) {
            return "";
        }
        String prefix = inputStr.substring(0, PREFIX_LEN).toUpperCase();
        if (prefix.equals(RCV) || prefix.equals(VCV)) {
            return prefix;
        }
        return "";
    }

    /**
     * Utility: strips the version suffix from a ClinVar ID, if present.
     * Example: RCV000123456.1 → RCV000123456
     */
    public static String stripVersion(String clinvarId) {
        if (clinvarId == null) return null;
        int dot = clinvarId.indexOf('.');
        return (dot >= 0) ? clinvarId.substring(0, dot) : clinvarId;
    }
}
