package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.InputParser;

import java.util.regex.Pattern;

public class DbsnpParser extends InputParser {
    static final String PREFIX = "^rs"; // think we need a full match here as there is likely to be
    // other identifiers e.g. pdb structure ID, or gene symbol that starts with "rs"

    static final Pattern PREFIX_PATTERN = Pattern.compile(PREFIX, Pattern.CASE_INSENSITIVE);
    static final Pattern PATTERN = Pattern.compile(PREFIX + "\\d+", Pattern.CASE_INSENSITIVE);

    public static boolean startsWithPrefix(String input) {
        return PREFIX_PATTERN.matcher(input).find();
    }

    public static boolean valid(String input) {
        return PATTERN.matcher(input).matches();
    }

    public static UserInput parse(String inputStr) {
        UserInput parsedInput = new UserInput(Format.DBSNP, inputStr.toLowerCase());// for db case-insensitive query to work
        // db table which stores RS IDs with lower-case prefix
        // ensures Rs, rS, RS work
        parsedInput.setFormat(Format.DBSNP);
        if (!valid(inputStr)) { // starts with prefix but doesn't match completely
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.DBSNP_ID_INVALID);
        }
        return parsedInput;
    }
}
