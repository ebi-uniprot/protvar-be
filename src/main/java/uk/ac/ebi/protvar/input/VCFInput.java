package uk.ac.ebi.protvar.input;

import lombok.Getter;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.Constants;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * VCF format
 * 1 CHROM
 * 2 POS
 * 3 ID     '.' if unknown
 * 4 REF
 * 5 ALT
 */
@Getter
public class VCFInput extends GenomicInput {
    public static final String FIELD_SEPERATOR = "\\s+";


    public VCFInput(String inputStr) {
        this.inputStr = inputStr;
        parseInputStr();
    }

    private void parseInputStr() {
        LinkedList<String> tokens = new LinkedList<>(Arrays.asList(this.inputStr.split(FIELD_SEPERATOR)));

        var userChromosome = Commons.trim(tokens.poll());
        String chromosome = convertChromosome(userChromosome);
        if (chromosome.equals(Constants.NA))
            addError(Constants.NOTE_INVALID_INPUT_CHROMOSOME + userChromosome);
        this.chr = chromosome;

        var userPosition = Commons.trim(tokens.poll());
        long position = convertPosition(userPosition);
        if (position <= 0)
            addError(Constants.NOTE_INVALID_INPUT_POSITION + userPosition);
        this.pos = position;

        if (isIdExist(tokens)) {
            var tokenId = Commons.trim(tokens.poll());
            this.id = tokenId.isEmpty() ? Constants.NA : tokenId;
        }else {
            this.id = Constants.NA;
        }

        var token = Commons.trim(tokens.poll());
        if (isReferenceAndAlternativeAllele(token)) {
            this.ref = token.split(Constants.SLASH)[0].toUpperCase();
            this.alt = token.split(Constants.SLASH)[1].toUpperCase();
        } else {
            if (isAllele(token))
                this.ref = token.toUpperCase();
            else {
                this.ref = Constants.NA;
                addError(Constants.NOTE_INVALID_INPUT_REF + token);
            }
            token = Commons.trim(tokens.poll());
            if (isAllele(token))
                this.alt = token.toUpperCase();
            else {
                this.alt = Constants.NA;
                addError(Constants.NOTE_INVALID_INPUT_ALT + token);
            }
        }
    }

    public static boolean startsWithChromo(String input) {
        String[] params = input.split(FIELD_SEPERATOR);
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

    public InputType.Gen getGenType() {
        return InputType.Gen.VCF;
    }

}
