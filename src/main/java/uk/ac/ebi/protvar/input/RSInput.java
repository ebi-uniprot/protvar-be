package uk.ac.ebi.protvar.input;

import lombok.Getter;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Getter
public class RSInput extends UserInput {
    public static final String RS_PREFIX = "rs";
    public static final String RS_ID_REGEX = RS_PREFIX + "(\\d+)";

    String id;
    List<GenomicInput> derivedGenomicInputs = new ArrayList<>();

    public RSInput(String inputStr) {
        this.inputStr = inputStr;
        this.id = inputStr;
    }

    @Override
    public InputType getType() {
        return InputType.RS;
    }

    @Override
    public String toString() {
        return "RSInput [id=" + id + "]";
    }


    public List<GenomeProteinMapping> derivedGenomicInputsMappings() {
        return derivedGenomicInputs.stream().map(GenomicInput::getMappings)
                .flatMap(List::stream).collect(Collectors.toList());
    }


    public static boolean isValid(String input) {
        return Pattern.matches(RSInput.RS_ID_REGEX, input);
    }

    public static boolean startsWithPrefix(String input) {
        return input.toLowerCase().startsWith(RS_PREFIX);
    }
}
