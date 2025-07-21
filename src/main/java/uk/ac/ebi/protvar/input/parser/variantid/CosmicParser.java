package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.InputParser;

import java.util.regex.Pattern;

public class CosmicParser extends InputParser {
    public static final int PREFIX_LEN = 4;
    // New ID prefix
    public static final String COSV = "COSV";
    // Legacy ID prefixes
    public static final String COSM = "COSM";
    public static final String COSN = "COSN";

    static final String PREFIX = String.format("^(%s|%s|%s)", COSV,COSM,COSN);

    static final Pattern PREFIX_PATTERN = Pattern.compile(PREFIX, Pattern.CASE_INSENSITIVE);

    static final Pattern FULL_PATTERN = Pattern.compile(PREFIX + "\\d+", Pattern.CASE_INSENSITIVE);

    public static boolean startsWithPrefix(String input) {
        return PREFIX_PATTERN.matcher(input).find();
    }

    public static boolean valid(String input) {
        return FULL_PATTERN.matcher(input).matches();
    }

    public static UserInput parse(String inputStr) {
        UserInput parsedInput = new UserInput(Format.COSMIC, inputStr);
        if (!valid(inputStr)) {
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.COSMIC_ID_INVALID);
        }
        return parsedInput;
    }

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
