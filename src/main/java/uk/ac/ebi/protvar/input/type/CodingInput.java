package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public abstract class CodingInput extends UserInput {
    private static final Logger LOGGER = LoggerFactory.getLogger(CodingInput.class);
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
