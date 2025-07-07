package uk.ac.ebi.protvar.input.format.protein;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.ProteinInput;

@Getter
@Setter
public class HGVSp extends ProteinInput {
    String rsAcc;

    public HGVSp(String inputStr) {
        super(inputStr);
        setFormat(Format.HGVS_PROT);
    }
}
