package uk.ac.ebi.protvar.input.format.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;

import java.util.regex.Pattern;

public class DbsnpID extends IDInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbsnpID.class);
    static final String PREFIX = "^rs";

    static final Pattern PREFIX_PATTERN = Pattern.compile(PREFIX, Pattern.CASE_INSENSITIVE);
    static final Pattern PATTERN = Pattern.compile(PREFIX + "\\d+", Pattern.CASE_INSENSITIVE);

    public DbsnpID(String inputStr) {
        super(inputStr);
        setFormat(Format.DBSNP);
        setId(inputStr.toLowerCase()); // for db case-insensitive query to work
        // db table which stores RS IDs with lower-case prefix
        // ensures Rs, rS, RS work
    }

    public static boolean startsWithPrefix(String input) {
        return PREFIX_PATTERN.matcher(input).find();
    }

    public static DbsnpID parse(String input) {
        DbsnpID parsedInput = new DbsnpID(input);
        if (!valid(input)) { // starts with prefix but doesn't match completely
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.DBSNP_ID_INVALID);
        }
        return parsedInput;
    }

    public static boolean valid(String input) {
        return PATTERN.matcher(input).matches();
    }

    @Override
    public String toString() {
        return String.format("DbsnpID [id=%s]", getId());
    }
}
