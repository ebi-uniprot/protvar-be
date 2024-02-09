package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.exception.InvalidInputException;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.RegexUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Supported custom formats:
 * 1. ACC
 * 2. ACC POS
 * 3. ACC POS X[ |/]Y
 * 4. ACC POS XXX[ |/]YYY
 * 5. ACC X999Y
 * 6. ACC [p.]XXX999YYY
 *
 * where
 * ACC=protein accession
 * 999=protein position
 * X/XXX=1-/3-letter ref AA
 * Y/YYY=1-/3-letter alt AA
 *
 */
@Getter
@Setter
public class ProteinInput extends UserInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProteinInput.class);

    // General patterns
    public static final String STOP_CODON = "\\*";

    // Base protein patterns
    // 1. Generic UniProt accession (valid format, but doesn't mean it exists)
    public static final String UNIPROT_ACC = "([O,P,Q][0-9][A-Z, 0-9]{3}[0-9]|[A-N,R-Z]([0-9][A-Z][A-Z, 0-9]{2}){1,2}[0-9])";

    // 2. Protein position (using same pattern as genomic position, or location)
    public static final String POS = GenomicInput.POS;

    // 3. Amino acid
    // a. one-letter (X|X|...|*)
    public final static String ONE_LETTER_AA = String.format("(%s)", String.join("|", AminoAcid.VALID_AA1)).replace("*", STOP_CODON);

    // b. three-letter (XXX|XXX|..|Ter)
    public final static String THREE_LETTER_AA = String.format("(%s)", String.join("|", AminoAcid.VALID_AA3));

    public final static String X_Y = ONE_LETTER_AA + RegexUtils.SPACES_OR_SLASH + ONE_LETTER_AA;
    public final static String XXX_YYY = THREE_LETTER_AA + RegexUtils.SPACES_OR_SLASH + THREE_LETTER_AA;

    // covers 1. and 2.
    public final static String CUSTOM_PROTEIN_ACC_POS = "(?<acc>" + UNIPROT_ACC + ")" +
            "("+ RegexUtils.SPACES + "(?<pos>"+ POS+"))?"; // optional pos

    // covers 3. and 4.
    public final static String CUSTOM_PROTEIN_ACC_POS_AA_AA = "(?<acc>" + UNIPROT_ACC + ")" +
            RegexUtils.SPACES +
            "(?<pos>"+ POS+")" +
            RegexUtils.SPACES +
            "(?<sub>("+X_Y + "|" + XXX_YYY +"))";

    // covers 5.
    public final static String CUSTOM_PROTEIN_ACC_X_POS_Y = "(?<acc>" + UNIPROT_ACC + ")" +
            RegexUtils.SPACES +
            "(?<ref>"+ONE_LETTER_AA+")" +
            "(?<pos>"+POS+")" +
            "(?<alt>"+ONE_LETTER_AA+")";

    // covers 6.
    public final static String CUSTOM_PROTEIN_ACC_XXX_POS_YYY = "(?<acc>" + UNIPROT_ACC + ")" +
            RegexUtils.SPACES + "(p.)?" +
            "(?<ref>"+THREE_LETTER_AA+")" +
            "(?<pos>"+POS+")" +
            "(?<alt>"+THREE_LETTER_AA+")";


    protected String acc; // UniProt accession
    protected Integer pos;
    protected String ref;
    protected String alt;

    List<GenomicInput> derivedGenomicInputs = new ArrayList<>();

    public ProteinInput(String inputStr) {
        setType(Type.PROTEIN);
        setFormat(Format.CUSTOM_PROT);
        setInputStr(inputStr);
    }

    public static boolean startsWithAccession(String input) {
        String[] params = input.split(RegexUtils.SPACES_OR_SLASH);
        if (params.length > 0){
            return validAccession(params[0]);
        }
        return false;
    }

    public static boolean validAccession(String acc) {
        return Pattern.matches(UNIPROT_ACC, acc);
    }

    public static ProteinInput parse(String inputStr) {
        ProteinInput parsedInput = new ProteinInput(inputStr);
        try {

            Pattern pattern = Pattern.compile(CUSTOM_PROTEIN_ACC_POS, Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(inputStr);
            if (matcher.matches()) {
                String acc = matcher.group("acc"); // uniprot accession
                String pos = matcher.group("pos");
                parsedInput.setAcc(acc);
                if (pos != null) {
                    parsedInput.setPos(Integer.parseInt(pos));
                }
                return parsedInput;
            }
            pattern = Pattern.compile(CUSTOM_PROTEIN_ACC_POS_AA_AA, Pattern.CASE_INSENSITIVE);
            matcher = pattern.matcher(inputStr);

            if (matcher.matches()) {
                String acc = matcher.group("acc"); // uniprot accession
                String pos = matcher.group("pos");
                parsedInput.setAcc(acc);
                parsedInput.setPos(Integer.parseInt(pos));
                String sub = matcher.group("sub");
                String[] aa = sub.split(RegexUtils.SPACES_OR_SLASH);
                parsedInput.setRef(AminoAcid.oneLetter(aa[0]));
                parsedInput.setAlt(AminoAcid.oneLetter(aa[1]));
                return parsedInput;
            }

            if (tryParseInput(CUSTOM_PROTEIN_ACC_X_POS_Y, inputStr, parsedInput))
                return parsedInput;
            else if (tryParseInput(CUSTOM_PROTEIN_ACC_XXX_POS_YYY, inputStr, parsedInput))
                return parsedInput;
            else {
                throw new InvalidInputException("No match");
            }
        }
        catch (Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_PROTEIN_INPUT);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }

    private static boolean tryParseInput(String regex, String inputStr, ProteinInput parsedInput) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher= pattern.matcher(inputStr);
        if (matcher.matches()) {
            String acc = matcher.group("acc"); // uniprot accession
            String pos = matcher.group("pos");
            String ref = matcher.group("ref");
            String alt = matcher.group("alt");
            parsedInput.setAcc(acc);
            parsedInput.setPos(Integer.parseInt(pos));
            parsedInput.setRef(AminoAcid.oneLetter(ref));
            parsedInput.setAlt(AminoAcid.oneLetter(alt));
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("ProteinInput [inputStr=%s, acc=%s, pos=%s, ref=%s, alt=%s]",
                getInputStr(), acc, pos, ref, alt);
    }

    public List<GenomeProteinMapping> derivedGenomicInputsMappings() {
        return derivedGenomicInputs.stream().map(GenomicInput::getMappings)
                .flatMap(List::stream).collect(Collectors.toList());
    }

    @Override
    public List<Object[]> chrPos() {
        return chrPosForDerivedGenomicInputs(derivedGenomicInputs);
    }

    @Override
    public List<GenomicInput> genInputs() {
        return derivedGenomicInputs;
    }
}
