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
 * 1. ACC                --> NOT SUPPORTED AS A VARIANT INPUT
 * 2. ACC POS            --> without AA substitution
 * 3. ACC POS X[ |/]Y    --> 3. & 4. effectively - ACC POS( AA[ |/]AA)
 * 4. ACC POS XXX[ |/]YYY
 * 5. ACC X999Y
 * 6. ACC [p.]XXX999YYY
 *
 * where
 * ACC=UniProt accession
 * POS=protein position
 * AA=1- or 3-letter ref/alt aa
 * X/XXX=1-/3-letter ref aa
 * Y/YYY=1-/3-letter alt aa
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
    public static final String UNIPROT_ACC = "([O,P,Q][0-9][A-Z, 0-9]{3}[0-9]|[A-N,R-Z]([0-9][A-Z][A-Z, 0-9]{2}){1,2}[0-9])" +
            "([-]([1-9][0-9]*))?";

    // 2. Protein position (using same pattern as genomic position, or location)
    public static final String POS = GenomicInput.POS;

    // 3. Amino acid
    // a. one-letter (X|X|...|*)
    public final static String ONE_LETTER_AA = String.format("(%s)", String.join("|", AminoAcid.VALID_AA1)).replace("*", STOP_CODON);

    // b. three-letter (XXX|XXX|..|Ter)
    public final static String THREE_LETTER_AA = String.format("(%s)", String.join("|", AminoAcid.VALID_AA3));
    public final static String THREE_LETTER_AA_INCL_STOP_AND_EQ = String.format("(%s|%s|=)", String.join("|", AminoAcid.VALID_AA3), STOP_CODON);

    public final static String X_Y = ONE_LETTER_AA + RegexUtils.SPACES_OR_SLASH + ONE_LETTER_AA;
    public final static String XXX_YYY = THREE_LETTER_AA + RegexUtils.SPACES_OR_SLASH + THREE_LETTER_AA;

    // covers 2-4.
    // ACC POS( REF( ALT)?)?
    public final static String CUSTOM_PROTEIN_ACC_POS_X_Y =
            "(?<acc>" + UNIPROT_ACC + ")" +
            RegexUtils.SPACES +
            "(?<pos>"+ POS+")" +
            "(" + RegexUtils.SPACES + "(?<ref>"+ ONE_LETTER_AA+")" +
            "(" + RegexUtils.SPACES_OR_SLASH + "(?<alt>"+ ONE_LETTER_AA +"))?)?";

    public final static String CUSTOM_PROTEIN_ACC_POS_XXX_YYY =
            "(?<acc>" + UNIPROT_ACC + ")" +
                    RegexUtils.SPACES +
                    "(?<pos>"+ POS+")" +
                    "(" + RegexUtils.SPACES + "(?<ref>"+ THREE_LETTER_AA+")" +
                    "(" + RegexUtils.SPACES_OR_SLASH + "(?<alt>"+ THREE_LETTER_AA +"))?)?";

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

    final static Pattern PATTERN_ACC = Pattern.compile(UNIPROT_ACC, Pattern.CASE_INSENSITIVE);
    final static Pattern PATTERN_ACC_POS_X_Y = Pattern.compile(CUSTOM_PROTEIN_ACC_POS_X_Y, Pattern.CASE_INSENSITIVE);
    final static Pattern PATTERN_ACC_POS_XXX_YYY = Pattern.compile(CUSTOM_PROTEIN_ACC_POS_XXX_YYY, Pattern.CASE_INSENSITIVE);
    final static Pattern PATTERN_ACC_X_POS_Y = Pattern.compile(CUSTOM_PROTEIN_ACC_X_POS_Y, Pattern.CASE_INSENSITIVE);
    final static Pattern PATTERN_ACC_XXX_POS_YYY = Pattern.compile(CUSTOM_PROTEIN_ACC_XXX_POS_YYY, Pattern.CASE_INSENSITIVE);


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
        return PATTERN_ACC.matcher(acc).matches();
    }

    public static ProteinInput parse(String inputStr) {
        ProteinInput parsedInput = new ProteinInput(inputStr);
        try {
            if (tryParseInput(PATTERN_ACC_POS_X_Y, inputStr, parsedInput))
                return parsedInput;
            if (tryParseInput(PATTERN_ACC_POS_XXX_YYY, inputStr, parsedInput))
                return parsedInput;
            if (tryParseInput(PATTERN_ACC_X_POS_Y, inputStr, parsedInput))
                return parsedInput;
            if (tryParseInput(PATTERN_ACC_XXX_POS_YYY, inputStr, parsedInput))
                return parsedInput;
            throw new InvalidInputException("No match found.");
        }
        catch (Exception ex) {
            parsedInput.addError(ErrorConstants.INVALID_PROTEIN_INPUT);
            LOGGER.error(parsedInput + ": parsing error", ex);
        }
        return parsedInput;
    }

    private static boolean tryParseInput(Pattern pattern, String inputStr, ProteinInput parsedInput) {
        Matcher matcher= pattern.matcher(inputStr);
        if (matcher.matches()) {
            String acc = matcher.group("acc"); // uniprot accession
            String pos = matcher.group("pos");
            String ref = matcher.group("ref");
            String alt = matcher.group("alt");
            parsedInput.setAcc(acc.toUpperCase());
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
