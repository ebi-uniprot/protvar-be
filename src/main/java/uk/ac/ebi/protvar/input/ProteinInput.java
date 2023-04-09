package uk.ac.ebi.protvar.input;

import lombok.Getter;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.Constants;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Supported formats:
 * ACC X 999 Y
 * ACC/X/999/Y
 * ACC X/999/Y
 * ACC 999 X Y
 * ACC 999    X     Y
 * ACC p.XXX999YYY
 * ACC X999Y
 * ACC 999 X/Y
 * ACC 999 XXX/YYY
 * ACC XXX999YYY
 * ACC/999/YYY
 * where
 * ACC=protein accession,
 * X=one letter ref AA, XXX=three letters ref AA (case-insensitive),
 * Y=one letter variant AA, YYY=three letters variant AA (case-insensitive),
 * 999=protein position.
 *
 * The order will be ACC first always.
 * Then either reference AA or position
 * Then either reference AA or position
 * Then variant last
 * The p. can just be ignored
 * The delimiters could vary but most likely whitespace or / and occasionally ,
 */
@Getter
public class ProteinInput extends UserInput {

    public static final String ACCESSION = "([O,P,Q][0-9][A-Z, 0-9]{3}[0-9]|[A-N,R-Z]([0-9][A-Z][A-Z, 0-9]{2}){1,2}[0-9])";
    public static final String POSITION = "(\\d+)";
    public final static String AA1 = String.format("([%s])", AminoAcid.VALID_AA1.stream().collect(Collectors.joining(",")));
    public final static String AA3 = String.format("(%s)", AminoAcid.VALID_AA3.stream().collect(Collectors.joining("|")));
    public static final String SPACES_OR_SLASH = "(\\s+|/)";

    public final static String PATTERN_ACC_X_999_Y = ACCESSION + "#" + AA1 + "#" + POSITION + "#" + AA1;
    public final static String PATTERN_ACC_999_X_Y = ACCESSION + "#" + POSITION + "#" + AA1 + "#" + AA1;
    public final static String PATTERN_ACC_999_XXX_YYY = ACCESSION + "#" + POSITION + "#" + AA3 + "#" + AA3;
    public final static String PATTERN_X999Y = AA1 + POSITION + AA1;
    public final static String PATTERN_XXX999YYY = AA3 + POSITION + AA3;
    public final static String PATTERN_pdotXXX999YYY = "([p|P].)" + AA3 + POSITION + AA3;
    public final static String PATTERN_ACC_X999Y = ACCESSION + "#" + PATTERN_X999Y;
    public final static String PATTERN_ACC_XXX999YYY = ACCESSION + "#" + PATTERN_XXX999YYY;
    public final static String PATTERN_ACC_pdotXXX999YYY = ACCESSION + "#" + PATTERN_pdotXXX999YYY;
    public final static String PATTERN_ACC_999_YYY = ACCESSION + "#" + POSITION + "#" + AA3;

    public final static String PROTEINS_FILE = "proteins.txt";
    public static Set<String> PROTEIN_ACCESSIONS;
    static {
        PROTEIN_ACCESSIONS = new BufferedReader(new InputStreamReader(ProteinInput.class.getClassLoader().getResourceAsStream(PROTEINS_FILE)))
                .lines()
                .collect(Collectors.toSet());
    }

    String acc;
    Long pos;
    String ref;
    String alt;

    List<GenomicInput> derivedGenomicInputs = new ArrayList<>();

    public ProteinInput(String inputStr) {
        this.inputStr = inputStr;
        parseInputStr();
        if (!PROTEIN_ACCESSIONS.contains(acc)) {
            addError(Constants.NOTE_INVALID_INPUT_NON_HUMAN_ACC + acc);
        }
    }

    private void setParams(String acc, Long pos, String ref, String alt) {
        this.acc = acc;
        this.pos = pos;
        this.ref = ref;
        this.alt = alt;
    }

    private void setError() {
        this.addError("Error parsing Protein input string: " + inputStr);
    }
    private void parseInputStr() {
        String line = this.inputStr.trim().toUpperCase();
        // ACC X 999 Y
        // ACC/X/999/Y
        // ACC X/999/Y
        if (Pattern.matches(PATTERN_ACC_X_999_Y.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            setParams(tokens[0], Long.valueOf(tokens[2]), tokens[1], tokens[3]);
        }
        // ACC 999 X Y
        // ACC 999    X     Y
        // ACC 999 X/Y
        else if (Pattern.matches(PATTERN_ACC_999_X_Y.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            setParams(tokens[0], Long.valueOf(tokens[1]), tokens[2], tokens[3]);
        }
        // ACC 999 XXX/YYY
        // ACC 999 XXX YYY
        else if (Pattern.matches(PATTERN_ACC_999_XXX_YYY.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            AminoAcid aaRef = AminoAcid.valueOf(tokens[2]);
            AminoAcid aaAlt = AminoAcid.valueOf(tokens[3]);
            setParams(tokens[0], Long.valueOf(tokens[1]), aaRef.getOneLetter(), aaAlt.getOneLetter());
        }
        // ACC X999Y
        else if (Pattern.matches(PATTERN_ACC_X999Y.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            Matcher m = Pattern.compile(PATTERN_X999Y).matcher(tokens[1]);
            if (m.find()) {
                Long pos = Long.valueOf(m.group(2));
                String r = m.group(1);
                String a = m.group(3);
                setParams(tokens[0], Long.valueOf(m.group(2)), m.group(1), m.group(3));
            } else
                setError();
        }
        // ACC XXX999YYY
        else if (Pattern.matches(PATTERN_ACC_XXX999YYY.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            Matcher m = Pattern.compile(PATTERN_XXX999YYY).matcher(tokens[1]);
            if (m.find()) {
                setParams(tokens[0], Long.valueOf(m.group(2)), AminoAcid.valueOf(m.group(1)).getOneLetter(), AminoAcid.valueOf(m.group(3)).getOneLetter());
            } else
                setError();
        }
        // ACC p.XXX999YYY
        else if (Pattern.matches(PATTERN_ACC_pdotXXX999YYY.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            Matcher m = Pattern.compile(PATTERN_pdotXXX999YYY).matcher(tokens[1]);
            if (m.find()) {
                setParams(tokens[0], Long.valueOf(m.group(3)), AminoAcid.valueOf(m.group(2)).getOneLetter(), AminoAcid.valueOf(m.group(4)).getOneLetter());
            } else
                setError();
        }
        // ACC/999/YYY
        else if (Pattern.matches(PATTERN_ACC_999_YYY.replace("#", SPACES_OR_SLASH), line)) {
            String tokens[] = line.split(SPACES_OR_SLASH);
            AminoAcid aaAlt = AminoAcid.valueOf(tokens[2]);
            setParams(tokens[0], Long.valueOf(tokens[1]), null, aaAlt.getOneLetter());
        }
        //setError();
    }

    @Override
    public InputType getType() {
        return InputType.PRO;
    }

    public static boolean validAccession(String acc) {
        return Pattern.matches(ACCESSION, acc);
    }

    public static boolean startsWithAccession(String input) {
        String[] params = input.split(SPACES_OR_SLASH);
        if (params.length > 0){
            return validAccession(params[0]);
        }
        return false;
    }

    @Override
    public String toString() {
        return "ProteinInput [acc=" + acc + ", pos=" + pos + ", ref=" + ref
                + ", alt=" + alt + "]";
    }

    public List<GenomeProteinMapping> derivedGenomicInputsMappings() {
        return derivedGenomicInputs.stream().map(GenomicInput::getMappings)
                .flatMap(List::stream).collect(Collectors.toList());
    }
}