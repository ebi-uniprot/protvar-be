package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.*;
import uk.ac.ebi.protvar.input.format.id.DbsnpID;
import uk.ac.ebi.protvar.model.response.GenomeProteinMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public List<GenomeProteinMapping> derivedGenomicInputsMappings() {
        return derivedGenomicInputs.stream().map(GenomicInput::getMappings)
                .flatMap(List::stream).collect(Collectors.toList());
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
