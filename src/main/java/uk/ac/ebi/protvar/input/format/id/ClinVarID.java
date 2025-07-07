package uk.ac.ebi.protvar.input.format.id;

import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;

public class ClinVarID extends IDInput {
    public ClinVarID(String inputStr) {
        super(inputStr);
        setFormat(Format.CLINVAR);
        setId(inputStr.toUpperCase()); // for db case-insensitive query
    }
}
