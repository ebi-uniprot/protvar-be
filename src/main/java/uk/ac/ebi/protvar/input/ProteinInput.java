package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProteinInput extends UserInput {
    // Parsed fields
    String accession;
    Integer position;
    String refAA;
    String altAA;

    // HGVSp input
    String refseqId;

    public ProteinInput(String inputStr) {
        super(Format.INTERNAL_PROTEIN, inputStr);
    }
}
