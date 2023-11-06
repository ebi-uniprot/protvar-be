package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CodingInput extends UserInput {

    String chr;
    Integer codingPos;
    String ref;
    String alt;

    List<GenomicInput> derivedGenomicInputs = new ArrayList<>();

    public CodingInput(String inputStr) {
        setType(Type.CODING);
        setInputStr(inputStr);
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
