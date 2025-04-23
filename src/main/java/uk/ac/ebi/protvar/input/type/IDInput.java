package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class IDInput extends UserInput {

    String id;
    List<GenomicInput> derivedGenomicInputs = new ArrayList<>();

    public IDInput(String inputStr) {
        super();
        this.setType(Type.ID);
        this.setInputStr(inputStr);
        this.setId(inputStr);
    }

    @Override
    public String toString() {
        return "ID [id=" + id + "]";
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
