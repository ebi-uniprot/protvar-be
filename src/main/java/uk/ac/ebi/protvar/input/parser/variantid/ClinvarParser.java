package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.InputParser;

import java.util.regex.Pattern;

/**
 * ClinVar Accession and version
 * SCV000000000.0  -> submission    X
 * RCV000000000.0  -> condition     /
 * VCV000000000.0  -> variant       /
 */
public class ClinvarParser extends InputParser {
    public static final int PREFIX_LEN = 3;
    public static final String RCV = "RCV";
    public static final String VCV = "VCV";

    static final String PREFIX = String.format("^(%s|%s)", RCV, VCV);
    static final Pattern PREFIX_PATTERN = Pattern.compile(PREFIX, Pattern.CASE_INSENSITIVE);
    static final Pattern PATTERN = Pattern.compile(PREFIX + "\\d{9}(\\.\\d+)?", Pattern.CASE_INSENSITIVE);

    public static boolean startsWithPrefix(String input) {
        return PREFIX_PATTERN.matcher(input).find(); // Check if the string starts with any of the prefixes (case-insensitive)
    }

    public static boolean valid(String input) {
        return PATTERN.matcher(input).matches();
    }

    public static VariantInput parse(String inputStr) {
        VariantInput parsedInput = new VariantInput(VariantFormat.CLINVAR, inputStr); // keep "raw" input, add parsedField "clinvarId" normalised
        if (!valid(inputStr)) {
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.CLINVAR_ID_INVALID);
        }
        return parsedInput;
    }

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
}
