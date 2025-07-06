package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class IDInput extends UserInput implements DerivedGenomicInputProvider {

    // TODO id is not needed for IDInput, we can use the raw inputStr

    String id; // this is not needed as in the case of InputType is ID, the inputStr is the id
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
    public List<Object[]> getChrPosList() {
        return DerivedGenomicInputProvider.super.getChrPosList();
    }
}
