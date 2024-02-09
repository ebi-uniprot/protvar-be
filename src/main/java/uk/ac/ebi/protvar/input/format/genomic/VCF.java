package uk.ac.ebi.protvar.input.format.genomic;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;
import uk.ac.ebi.protvar.utils.RegexUtils;

import static uk.ac.ebi.protvar.utils.RegexUtils.*;

/**
 * StrictVCF format has 8 fixed and mandatory columns:
 * 1. CHROM    <- REQUIRED
 * 2. POS      <- REQUIRED
 * 3. ID       <- REQUIRED ('.' or actual ID)
 * 4. REF      <- REQUIRED
 * 5. ALT      <- REQUIRED
 * 6. QUAL     <- IGNORED
 * 7. FILTER   <- IGNORED
 * 8. INFO     <- IGNORED
 * ...         <- IGNORED
 * DELIMITER = SPACES
 */
@Getter
public class VCF extends GenomicInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(VCF.class);

    public static final String ID = "(.|[a-zA-Z0-9]+)"; // '.' if unknown or any alphanum string

    public static final String REGEX = CHR + SPACES +
            POS + SPACES +
            ID + SPACES +
            BASE + SPACES + BASE +
            "((\\s?)|(\\s?).?)?"; // everything after the base substitution is optional

    private VCF(String inputStr) {
        super(inputStr);
        setFormat(Format.VCF);
    }

    public static boolean isValid(String inputStr) {
        inputStr = Commons.trim(inputStr);
        return RegexUtils.matchIgnoreCase(REGEX, inputStr);
    }

    public static VCF parse(String inputStr) {
        // pre-condition: isValid
        VCF parsedInput = new VCF(inputStr);
        try {
            String[] params = inputStr.split(SPACES);
            String chr = convertChromosome(params[0]);
            Integer pos = convertPosition(params[1]);
            String id = convertId(params[2]);
            String ref = params[3].toUpperCase();
            String alt = params[4].toUpperCase();

            parsedInput.setChr(chr);
            parsedInput.setPos(pos);
            parsedInput.setId(id);
            parsedInput.setRef(ref);
            parsedInput.setAlt(alt);

            // Skip check here - done later after ref base is checked to be correct
            //if (ref.equals(alt)) {
            //    parsedInput.addWarning("Ref and alt base are the same");
            //}
        }
        catch(Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_VCF_INPUT);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }

    public static String convertId(String id) {
        id = id.trim();
        if (id.equals("."))
            return Constants.NA;
        return id;
    }

    @Override
    public String toString() {
        return String.format("VCF [inputStr=%s, chr=%s, pos=%s, ref=%s, alt=%s]",
                getInputStr(), getChr(), getPos(), getRef(), getAlt());
    }
}
