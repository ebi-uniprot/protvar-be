package uk.ac.ebi.protvar.input.format.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Pattern;

public class ClinVarID extends IDInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClinVarID.class);
    // SCV000000000.0
    // RCV000000000.0
    // VCV000000000.0
    public static final String PREFIX = "(SCV|RCV|VCV)";
    public static final String REGEX = PREFIX + "(\\d+)(\\.\\d+)?";

    public ClinVarID(String inputStr) {
        super(inputStr);
        setFormat(Format.CLINVAR);
    }

    public static boolean startsWithPrefix(String input) {
        return Pattern.matches("^"+PREFIX, input.toUpperCase());
    }

    public static ClinVarID parse(String input) {
        ClinVarID parsedInput = new ClinVarID(input);
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
        return String.format("ClinVarID [id=%s]", getId());
    }
}
