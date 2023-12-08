package uk.ac.ebi.protvar.input.format.id;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Pattern;

/**
 * ClinVar Accession and version
 * SCV000000000.0  -> submission    X
 * RCV000000000.0  -> condition     /
 * VCV000000000.0  -> variant       /
 */
public class ClinVarID extends IDInput {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinVarID.class);
    public static final String RCV = "RCV";
    public static final String VCV = "VCV";


    public static final int PREFIX_LEN = 3;
    public static final String PREFIX = String.format("(%s|%s)", RCV, VCV);
    public static final String REGEX = PREFIX + "(\\d+)(\\.\\d+)?";

    public ClinVarID(String inputStr) {
        super(inputStr);
        setFormat(Format.CLINVAR);
    }

    public static boolean startsWithPrefix(String input) {
        return Pattern.matches("^"+PREFIX+"(.*)$", input.toUpperCase());
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
