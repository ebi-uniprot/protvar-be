package uk.ac.ebi.protvar.input;

public class GnomadInput extends GenomicInput {
    public static final String GNOMAD_INPUT_SEP = "-";
    public static final String GNOMAD_ID_REGEX = "(2[0-2]|1[0-9]|[1-9]|X|Y)-(\\d+)-(A|T|C|G)-(A|T|C|G)";

    public GnomadInput(String inputStr) {
        // will have passed regex to be here
        this.inputStr = inputStr;

        try {
            String[] inputArr = inputStr.split(GNOMAD_INPUT_SEP);
            // let catch handle null or index exception
            this.chr = inputArr[0];
            Long pos = Long.parseLong(inputArr[1]);
            this.pos = pos;
            this.ref = inputArr[2];
            this.alt = inputArr[3];
        }
        catch (Exception ex) {
            this.addInvalidReason("Error parsing gnomAD input string");
        }
    }

    public InputType.Gen getGenType() {
        return InputType.Gen.GNOMAD;
    }
}
