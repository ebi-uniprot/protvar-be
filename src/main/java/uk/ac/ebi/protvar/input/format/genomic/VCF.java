package uk.ac.ebi.protvar.input.format.genomic;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // "s" stands for space
    // "S" stands for non-space

    /** regex pattern for the first five columns of a VCF format string
     * ^(\\S+): Matches the first non-whitespace sequence at the start of the line (CHROM).
     * (\\d+): Matches one or more digits (POS).
     * (\\S+): Matches the next non-whitespace sequence (ID).
     * (\\S+): Matches the next non-whitespace sequence (REF).
     * (\\S+): Matches the next non-whitespace sequence (ALT).
     *
     * \\t: Matches a tab character. (strict VCF, needs to be a tab)
     * replaced \\t with \\s+
     * also replace \\d+ for pos with \\S+, check is done in parse
     */
    static final Pattern PATTERN = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)", Pattern.CASE_INSENSITIVE);

    private VCF(String inputStr) {
        super(inputStr);
        setFormat(Format.VCF);
    }

    public static boolean matchesPattern(String inputStr) {
        return PATTERN.matcher(inputStr).find();
    }

    public static VCF parse(String inputStr) {
        // pre-condition: matchesPattern
        VCF parsedInput = new VCF(inputStr);
        try {
            Matcher matcher = PATTERN.matcher(inputStr);
            if (matcher.find()) {
                String chr = matcher.group(1);
                String pos = matcher.group(2);
                String id = matcher.group(3);
                String ref = matcher.group(4);
                String alt = matcher.group(5);

                GenomicInput.parseChr(chr, parsedInput);
                GenomicInput.parsePos(pos, parsedInput);
                GenomicInput.parseId(id, parsedInput);
                GenomicInput.parseRef(ref, parsedInput);
                GenomicInput.parseAlt(alt, parsedInput);

                // Skip check here - done later after ref base is checked to be correct
                //if (ref.equals(alt)) {
                //    parsedInput.addWarning("Ref and alt base are the same");
                //}
            } else {
                throw new InvalidInputException("No match found.");
            }
        } catch(Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_VCF_INPUT);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }

    @Override
    public String toString() {
        return String.format("VCF [inputStr=%s, chr=%s, pos=%s, ref=%s, alt=%s]",
                getInputStr(), getChr(), getPos(), getRef(), getAlt());
    }
}
