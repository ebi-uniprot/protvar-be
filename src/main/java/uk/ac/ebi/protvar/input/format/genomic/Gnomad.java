package uk.ac.ebi.protvar.input.format.genomic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gnomad extends GenomicInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(Gnomad.class);

    public static final String GNOMAD_SEP = "-";
    public static final String GNOMAD_ID_REGEX = CHR + GNOMAD_SEP + POS + GNOMAD_SEP + BASE + GNOMAD_SEP + BASE;

    private Gnomad(String inputStr) {
        super(inputStr);
        setFormat(Format.GNOMAD);
    }

    public static boolean isValid(String input) {
        Pattern pattern = Pattern.compile(GNOMAD_ID_REGEX, Pattern.CASE_INSENSITIVE);
        Matcher matcher= pattern.matcher(input);
        return matcher.matches();
    }

    public static Gnomad parse(String inputStr) {
        // pre-condition: isValid
        Gnomad parsedInput = new Gnomad(inputStr);
        try {
            String[] inputArr = inputStr.split(GNOMAD_SEP);
            // let catch handle null or index exception
            parsedInput.setChr(inputArr[0]);
            parsedInput.setPos(Integer.parseInt(inputArr[1]));
            parsedInput.setRef(inputArr[2]);
            parsedInput.setAlt(inputArr[3]);
        }
        catch (Exception ex) {
            String msg = parsedInput + ": parsing error";
            parsedInput.addError(msg);
            LOGGER.error(msg, ex);
        }
        return parsedInput;
    }

    public static boolean startsWithChromo(String input) {
        return GenomicInput.startsWithChromo(input, GNOMAD_SEP);
    }

    @Override
    public String toString() {
        return String.format("Gnomad [inputStr=%s]", getInputStr());
    }
}
