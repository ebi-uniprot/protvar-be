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

    // output or result of input
    List<GenomeProteinMapping> mappings = new ArrayList<>();

    public CodingInput(String inputStr) {
        setType(Type.CODING);
        setInputStr(inputStr);
    }

}
