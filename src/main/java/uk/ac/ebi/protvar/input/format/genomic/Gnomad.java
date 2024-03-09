package uk.ac.ebi.protvar.input.format.genomic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gnomad extends GenomicInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(Gnomad.class);

    public static final String DASH = "-";
    public static final String ANY = "[^-]+";

    public static final String GNOMAD_GENERAL_PATTERN = "(?<chr>"+ANY + ")" +
            DASH +
            "(?<pos>"+ANY + ")" +
            DASH +
            "(?<ref>"+ANY + ")" +
            DASH +
            "(?<alt>"+ANY + ")";

    public static final String GNOMAD_STRICT_PATTERN = CHR + DASH + POS + DASH + BASE + DASH + BASE;

    private static Pattern p = Pattern.compile(GNOMAD_GENERAL_PATTERN, Pattern.CASE_INSENSITIVE);
    private static Pattern pattern = Pattern.compile(GNOMAD_STRICT_PATTERN, Pattern.CASE_INSENSITIVE);

    private Gnomad(String inputStr) {
        super(inputStr);
        setFormat(Format.GNOMAD);
    }

    // Pre-check (level 1)
    // Pattern: ?-?-?-?
    public static boolean preCheck(String input) {
        return RegexUtils.matchIgnoreCase(GNOMAD_GENERAL_PATTERN, input);
    }

    public static boolean isValid(String input) {
        Pattern pattern = Pattern.compile(GNOMAD_STRICT_PATTERN, Pattern.CASE_INSENSITIVE);
        Matcher matcher= pattern.matcher(input);
        return matcher.matches();
    }

    public static Gnomad parse(String inputStr) {
        // pre-condition: preCheck
        Gnomad parsedInput = new Gnomad(inputStr);
        try {
            Matcher matcher = p.matcher(inputStr);
            if (matcher.matches()) {
                String chr = matcher.group("chr");
                String pos = matcher.group("pos");
                String ref = matcher.group("ref");
                String alt = matcher.group("alt");

                if (RegexUtils.matchIgnoreCase(CHR, chr))
                    parsedInput.setChr(chr);
                else
                    parsedInput.addError(ErrorConstants.INVALID_CHR);

                if (RegexUtils.matchIgnoreCase(POS, pos))
                    parsedInput.setPos(Integer.parseInt(pos));
                else
                    parsedInput.addError(ErrorConstants.INVALID_POS);

                if (RegexUtils.matchIgnoreCase(BASE, ref))
                    parsedInput.setRef(ref);
                else
                    parsedInput.addError(ErrorConstants.INVALID_REF);

                if (RegexUtils.matchIgnoreCase(BASE, alt))
                    parsedInput.setAlt(alt);
                else
                    parsedInput.addError(ErrorConstants.INVALID_ALT);

            } else
                throw new InvalidInputException("No match");
        } catch (Exception ex) {
            LOGGER.error(parsedInput + ": parsing error", ex);
            parsedInput.addError(ErrorConstants.INVALID_GNOMAD);
        }
        return parsedInput;
    }

    public static boolean startsWithChromo(String input) {
        return GenomicInput.startsWithChromo(input, DASH);
    }

    @Override
    public String toString() {
        return String.format("Gnomad [inputStr=%s]", getInputStr());
    }
}
