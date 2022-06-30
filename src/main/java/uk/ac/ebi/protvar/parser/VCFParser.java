package uk.ac.ebi.protvar.parser;

import uk.ac.ebi.protvar.model.UserInput;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class VCFParser extends GenericParser {

    public static UserInput parse(String input) {
        if (input == null || input.isBlank())
            return UserInput.invalidObject(input, UserInput.Type.VCF);

        input = input.trim();
        UserInput userInput = new UserInput(UserInput.Type.VCF);
        userInput.setInputString(input);
        LinkedList<String> tokens = new LinkedList<>(Arrays.asList(input.split(FIELD_SEPERATOR)));

        var userChromosome = Commons.trim(tokens.poll());
        String chromosome = convertChromosome(userChromosome);
        if (chromosome.equals(Constants.NA))
            userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_CHROMOSOME + userChromosome);
        userInput.setChromosome(chromosome);

        var userPosition = Commons.trim(tokens.poll());
        long position = convertPosition(userPosition);
        if (position <= 0)
            userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_POSITION + userPosition);
        userInput.setStart(position);

        if (isIdExist(tokens)) {
            var tokenId = Commons.trim(tokens.poll());
            userInput.setId(tokenId.isEmpty() ? Constants.NA : tokenId);
        }else {
            userInput.setId(Constants.NA);
        }

        var token = Commons.trim(tokens.poll());
        if (isReferenceAndAlternativeAllele(token)) {
            userInput.setRef(token.split(Constants.SLASH)[0].toUpperCase());
            userInput.setAlt(token.split(Constants.SLASH)[1].toUpperCase());
        } else {
            if (isAllele(token))
                userInput.setRef(token.toUpperCase());
            else {
                userInput.setRef(Constants.NA);
                userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_REF + token);
            }
            token = Commons.trim(tokens.poll());
            if (isAllele(token))
                userInput.setAlt(token.toUpperCase());
            else {
                userInput.setAlt(Constants.NA);
                userInput.addInvalidReason(Constants.NOTE_INVALID_INPUT_ALT + token);
            }
        }
        return userInput;
    }

    public static boolean startsWithChromo(String input) {
        String[] params = input.split(" ");
        if (params.length > 0){
            String p1 = params[0].toUpperCase();
            return CHROMO_1_23.contains(p1) || CHROMO_X_Y.contains(p1) || CHROMO_MT.contains(p1);
        }
        return false;
    }
    public static boolean isIdExist(LinkedList<String> remainingTokens) {
        if(remainingTokens.isEmpty())
            return false;
        if(isReferenceAndAlternativeAllele(remainingTokens.getFirst()))
            return false;
        return !isAllele(remainingTokens.getFirst());
    }
    public static boolean isReferenceAndAlternativeAllele(String element){
        element = Commons.trim(element);
        return element.length() == 3 && element.contains(Constants.SLASH) && isAllele(element.split(Constants.SLASH)[0]) && isAllele(element.split(Constants.SLASH)[1]);
    }

}
