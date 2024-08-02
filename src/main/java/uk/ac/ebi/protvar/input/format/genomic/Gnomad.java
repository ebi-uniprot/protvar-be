package uk.ac.ebi.protvar.input.format.genomic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gnomad extends GenomicInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(Gnomad.class);

    /**
     * [^\\s-]+: Matches one or more characters that are neither whitespace (\s) nor dash (-).
     * The ^ inside the square brackets negates the character class, so it matches any character that is
     * not a whitespace or a dash.
     */
    static final Pattern PATTERN = Pattern.compile("^([^\\s-]+)-([^\\s-]+)-([^\\s-]+)-([^\\s-]+)$", Pattern.CASE_INSENSITIVE);

    private Gnomad(String inputStr) {
        super(inputStr);
        setFormat(Format.GNOMAD);
    }

    // Level 1 check
    // Matches pattern: ?-?-?-?
    public static boolean matchesPattern(String inputStr) {
        return PATTERN.matcher(inputStr).find();
    }

    public static Gnomad parse(String inputStr) {
        // pre-condition: matchesPattern
        Gnomad parsedInput = new Gnomad(inputStr);
        try {
            Matcher matcher = PATTERN.matcher(inputStr);
            if (matcher.matches()) {
                String chr = matcher.group(1);
                String pos = matcher.group(2);
                String ref = matcher.group(3);
                String alt = matcher.group(4);

                GenomicInput.parseChr(chr, parsedInput);
                GenomicInput.parsePos(pos, parsedInput);
                GenomicInput.parseRef(ref, parsedInput);
                GenomicInput.parseAlt(alt, parsedInput);
            } else {
                throw new InvalidInputException("No match found.");
            }
        } catch (Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_GNOMAD);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }

    @Override
    public String toString() {
        return String.format("Gnomad [inputStr=%s]", getInputStr());
    }
}
