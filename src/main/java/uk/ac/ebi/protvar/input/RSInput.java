package uk.ac.ebi.protvar.input;

import lombok.Getter;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class RSInput extends UserInput {
    public static final String RS_ID_REGEX = "rs(\\d+)";

    String id;
    List<GenomicInput> genomicInputList = new ArrayList<>();

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

    public List<GenomeProteinMapping> getMappings() {
        return genomicInputList.stream().map(GenomicInput::getMappings)
                .flatMap(List::stream).collect(Collectors.toList());
    }
}
