package uk.ac.ebi.protvar.parser;

import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.utils.AminoAcid;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Accepted input formats:
 * ACC L558A  (one letter AA rep)
 * ACC 558 L/A
 * Possible future formats:
 * ACC 558 L A
 * ACC 558 Leu/Ala (or Leu Ala) (3 letters AA rep)
 */
public class ProtACParser extends GenericParser {

    public static final String REGEX_ACCESSION = "[O,P,Q][0-9][A-Z, 0-9]{3}[0-9]|[A-N,R-Z]([0-9][A-Z][A-Z, 0-9]{2}){1,2}[0-9]";

    // format e.g. L558A
    public final static String AA_POS_AA_PATTERN = "([%s])(\\d+)([%s])".replace("%s", AminoAcid.VALID_LETTERS);

    // format L/A
    public final static String AA_AA_PATTERN = ("([%s])/([%s])").replace("%s", AminoAcid.VALID_LETTERS);


    public static UserInput parse(String input) {
        if (input == null || input.isBlank())
            return UserInput.invalidObject(input, UserInput.Type.PROTAC);

        input = input.trim();
        UserInput userInput = new UserInput(UserInput.Type.PROTAC);
        userInput.setInputString(input);
        LinkedList<String> tokens = new LinkedList<>(Arrays.asList(input.split(FIELD_SEPERATOR)));

        var accession = Commons.trim(tokens.poll());
        if (!validAccession(accession))
            userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_ACCESSION + accession);
        userInput.setAccession(accession);

        long proteinPosition = -1L;
        String oneLetterRefAA = null;
        String oneLetterAltAA = null;

        String aaInput = "";

        var token = Commons.trim(tokens.poll());
        if (token.matches("\\d+")) {
            proteinPosition = convertPosition(token);
            var aaToken = Commons.trim(tokens.poll());
            if (aaToken.matches(AA_AA_PATTERN)) {
                oneLetterRefAA = String.valueOf(aaToken.charAt(0));
                oneLetterAltAA = String.valueOf(aaToken.charAt(2));
            } else {
                aaInput = aaToken;
            }
        } else {
            if (token.matches(AA_POS_AA_PATTERN)) {
                oneLetterRefAA = token.substring(0, 1);
                proteinPosition = convertPosition(token.substring(1, token.length()-1));
                oneLetterAltAA = token.substring(token.length()-1);
            } else {
                aaInput = token;
            }
        }
        userInput.setProteinPosition(proteinPosition);
        userInput.setOneLetterRefAA(oneLetterRefAA);
        userInput.setOneLetterAltAA(oneLetterAltAA);

        if (proteinPosition <= 0)
            userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_POSITION + proteinPosition);
        if (oneLetterRefAA == null || oneLetterAltAA == null)
            userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_AA + aaInput);

        if (oneLetterRefAA != null && oneLetterAltAA != null) {
            AminoAcid refAA = AminoAcid.fromOneLetter(oneLetterRefAA);
            AminoAcid altAA = AminoAcid.fromOneLetter(oneLetterAltAA);
            Set<Integer> changedPosSet = refAA.changedPositions(altAA);
            if (changedPosSet == null || changedPosSet.isEmpty())
                userInput.addInvalidReason(refAA.formatted() + " -> " + altAA.formatted() + " not possible via SNV");
        }

        return userInput;
    }

    public static boolean validAccession(String acc) {
        return Pattern.matches(REGEX_ACCESSION, acc);
    }

    public static boolean startsWithAccession(String input) {
        String[] params = input.split(" ");
        if (params.length > 0){
            return validAccession(params[0]);
        }
        return false;
    }

}