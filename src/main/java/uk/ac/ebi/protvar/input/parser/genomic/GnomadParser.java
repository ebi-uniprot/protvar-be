package uk.ac.ebi.protvar.input.parser.genomic;

import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.parser.InputParser;
import uk.ac.ebi.protvar.input.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GnomadParser extends InputParser {

    /**
     * [^\\s-]+: Matches one or more characters that are neither whitespace (\s) nor dash (-).
     * The ^ inside the square brackets negates the character class, so it matches any character that is
     * not a whitespace or a dash.
     */

    public static final Pattern PATTERN = Pattern.compile("^([^\\s-]+)-([^\\s-]+)-([^\\s-]+)-([^\\s-]+)$", Pattern.CASE_INSENSITIVE);

    // Level 1 check
    // Matches pattern: ?-?-?-?
    public static boolean matchesPattern(String inputStr) {
        return PATTERN.matcher(inputStr).find();
    }

    public static GenomicInput parse(String inputStr) {
        // pre-condition: matchesPattern
        GenomicInput parsedInput = new GenomicInput(inputStr);
        parsedInput.setFormat(Format.GNOMAD);
        try {
            Matcher matcher = PATTERN.matcher(inputStr);
            if (matcher.matches()) {
                String chr = matcher.group(1);
                String pos = matcher.group(2);
                String ref = matcher.group(3);
                String alt = matcher.group(4);

                GenomicParser.parseChr(chr, parsedInput);
                GenomicParser.parsePos(pos, parsedInput);
                GenomicParser.parseRef(ref, parsedInput);
                GenomicParser.parseAlt(alt, parsedInput);
            } else {
                throw new InvalidInputException("No match found.");
            }
        } catch (Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_GNOMAD);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }
}
