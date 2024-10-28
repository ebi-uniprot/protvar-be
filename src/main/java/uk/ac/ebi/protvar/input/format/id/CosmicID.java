package uk.ac.ebi.protvar.input.format.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;

import java.util.regex.Pattern;

public class CosmicID extends IDInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(CosmicID.class);

    public static final int PREFIX_LEN = 4;
    // New ID prefix
    public static final String COSV = "COSV";
    // Legacy ID prefixes
    public static final String COSM = "COSM";
    public static final String COSN = "COSN";

    static final String PREFIX = String.format("^(%s|%s|%s)", COSV,COSM,COSN);

    static final Pattern PREFIX_PATTERN = Pattern.compile(PREFIX, Pattern.CASE_INSENSITIVE);

    static final Pattern FULL_PATTERN = Pattern.compile(PREFIX + "\\d+", Pattern.CASE_INSENSITIVE);

    public CosmicID(String inputStr) {
        super(inputStr);
        setFormat(Format.COSMIC);
        setId(inputStr.toUpperCase()); // for db case-insensitive query
    }

    public static boolean startsWithPrefix(String input) {
        return PREFIX_PATTERN.matcher(input).find();
    }

    public static CosmicID parse(String input) {
        CosmicID parsedInput = new CosmicID(input);
        if (!valid(input)) {
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.COSMIC_ID_INVALID);
        }
        return parsedInput;
    }

    public static boolean valid(String input) {
        return FULL_PATTERN.matcher(input).matches();
    }

    @Override
    public String toString() {
        return String.format("CosmicID [id=%s]", getId());
    }
}
