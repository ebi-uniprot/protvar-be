package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.input.parser.InputParser;

import java.util.regex.Pattern;

public class DbsnpInputParser extends InputParser {
    static final String PREFIX = "^rs";

    static final Pattern PREFIX_PATTERN = Pattern.compile(PREFIX, Pattern.CASE_INSENSITIVE);
    static final Pattern PATTERN = Pattern.compile(PREFIX + "\\d+", Pattern.CASE_INSENSITIVE);

    public static boolean startsWithPrefix(String input) {
        return PREFIX_PATTERN.matcher(input).find();
    }

    public static boolean valid(String input) {
        return PATTERN.matcher(input).matches();
    }

    public static DbsnpID parse(String input) {
        DbsnpID parsedInput = new DbsnpID(input);
        if (!valid(input)) { // starts with prefix but doesn't match completely
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.DBSNP_ID_INVALID);
        }
        return parsedInput;
    }
}
