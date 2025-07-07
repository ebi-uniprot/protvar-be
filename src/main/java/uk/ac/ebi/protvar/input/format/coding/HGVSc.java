package uk.ac.ebi.protvar.input.format.coding;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.CodingInput;

@Getter
@Setter
public class HGVSc extends CodingInput {
    String rsAcc; // refseq id
    Integer pos; // Coding DNA position
    String ref;
    String alt;
    // optional
    String gene;
    String protRef;
    String protAlt;
    Integer protPos;

    // derived
    String derivedUniprotAcc;
    Integer derivedProtPos;
    Integer derivedCodonPos;

    public HGVSc(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_CODING);
    }
}
