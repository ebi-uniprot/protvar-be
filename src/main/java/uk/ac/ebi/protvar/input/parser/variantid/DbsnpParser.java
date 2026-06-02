package uk.ac.ebi.protvar.input.parser.variantid;

import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.parser.VariantParser;

import java.util.regex.Pattern;

public class DbsnpParser extends VariantParser {

    // Regex            Note
    // ^rs\d+$          Allows any number of digits (no upper limit, most flexible)
    // ^rs\d{1,15}$     Matches current rsID range (reasonable limit, past+near future)

    // Structural pattern - quick check for rs + digit pattern
    private static final Pattern STRUCTURE_PATTERN = Pattern.compile("^rs\\d", Pattern.CASE_INSENSITIVE);

    // Full validation pattern: rs followed by one or more digits, nothing else
    private static final Pattern FULL_PATTERN = Pattern.compile("^rs\\d+$", Pattern.CASE_INSENSITIVE);

    // todo Check prefix check; might need a full pattern match as there is likely to be
    //  other identifiers e.g. pdb structure ID, or gene symbol that starts with "rs"

    /**
     * Quick structural check - just verifies it starts with rs + digit
     */
    public static boolean matchesStructure(String input) {
        return input != null && STRUCTURE_PATTERN.matcher(input).lookingAt();
    }

    /**
     * Full pattern validation - validates complete rsID format
     */
    public static boolean matchesPattern(String input) {
        return input != null && FULL_PATTERN.matcher(input).matches();
    }

    public static VariantInput parse(String inputStr) {
        String normalized = inputStr.toLowerCase(); // for db table match
        VariantInput parsedInput = new VariantInput(VariantFormat.DBSNP, normalized);
        if (!matchesPattern(inputStr)) {
            LOGGER.warn("Invalid dbSNP ID format: {}", inputStr);
            parsedInput.addError(ErrorConstants.DBSNP_ID_INVALID);
        }
        return parsedInput;
    }
}
