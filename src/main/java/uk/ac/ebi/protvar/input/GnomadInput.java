package uk.ac.ebi.protvar.input;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GnomadInput extends GenomicInput {

    public static final String GNOMAD_SEP = "-";
    public static final String GNOMAD_ID_REGEX = CHR + GNOMAD_SEP + POS + GNOMAD_SEP + DNA + GNOMAD_SEP + DNA;

    public GnomadInput(String inputStr) {
        // will have passed regex to be here
        this.inputStr = inputStr;

        try {
            String[] inputArr = inputStr.split(GNOMAD_SEP);
            // let catch handle null or index exception
            this.chr = inputArr[0];
            Long pos = Long.parseLong(inputArr[1]);
            this.pos = pos;
            this.ref = inputArr[2];
            this.alt = inputArr[3];
        }
        catch (Exception ex) {
            this.addError("Error parsing gnomAD input string");
        }
    }

    public InputType.Gen getGenType() {
        return InputType.Gen.GNOMAD;
    }

    public static boolean isValid(String input) {
        Pattern pattern = Pattern.compile(GNOMAD_ID_REGEX, Pattern.CASE_INSENSITIVE);
        Matcher matcher= pattern.matcher(input);
        return matcher.matches();
    }

    public static boolean startsWithChromo(String input) {
        return GenomicInput.startsWithChromo(input, GNOMAD_SEP);
    }
}
