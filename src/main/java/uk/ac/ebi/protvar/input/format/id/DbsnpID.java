package uk.ac.ebi.protvar.input.format.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;
import uk.ac.ebi.protvar.utils.RegexUtils;

public class DbsnpID extends IDInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(DbsnpID.class);
    public static final String PREFIX = "rs";
    public static final String REGEX = PREFIX + "(\\d+)";

    public DbsnpID(String inputStr) {
        super(inputStr);
        setFormat(Format.DBSNP);
    }

    public static boolean startsWithPrefix(String input) {
        return  input.toLowerCase().startsWith(PREFIX);
    }

    public static DbsnpID parse(String input) {
        DbsnpID parsedInput = new DbsnpID(input);
        if (!isValid(input)) {
            LOGGER.warn(parsedInput + ": parsing error");
            parsedInput.addError(ErrorConstants.DBSNP_ID_INVALID);
        }
        return parsedInput;
    }

    public static boolean isValid(String input) {
        return RegexUtils.matchIgnoreCase(REGEX, input);
    }

    @Override
    public String toString() {
        return String.format("DbsnpID [id=%s]", getId());
    }
}
