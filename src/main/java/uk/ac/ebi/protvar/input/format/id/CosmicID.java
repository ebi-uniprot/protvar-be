package uk.ac.ebi.protvar.input.format.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Pattern;

public class CosmicID extends IDInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(CosmicID.class);

    // New ID prefix
    public static final String COSV = "COSV";
    // Legacy ID prefixes
    public static final String COSM = "COSM";
    public static final String COSN = "COSN";

    public static final int PREFIX_LEN = 4;

    public static final String PREFIX = String.format("(%s|%s|%s)", COSV,COSM,COSN);
    public static final String REGEX = PREFIX + "(\\d+)";

    public CosmicID(String inputStr) {
        super(inputStr);
        setFormat(Format.COSMIC);
    }

    public static boolean startsWithPrefix(String input) {
        return Pattern.matches("^"+PREFIX+"(.*)$", input.toUpperCase());
    }

    public static CosmicID parse(String input) {
        CosmicID parsedInput = new CosmicID(input);
        if (!isValid(input)) {
            String msg = parsedInput + ": parsing error";
            parsedInput.addError(msg);
            LOGGER.warn(msg);
        }
        return parsedInput;
    }

    public static boolean isValid(String input) {
        return RegexUtils.matchIgnoreCase(REGEX, input);
    }

    @Override
    public String toString() {
        return String.format("CosmicID [id=%s]", getId());
    }
}
