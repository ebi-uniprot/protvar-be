package uk.ac.ebi.protvar.input.format.id;

import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.IDInput;

public class CosmicID extends IDInput {
    public CosmicID(String inputStr) {
        super(inputStr);
        setFormat(Format.COSMIC);
        setId(inputStr.toUpperCase()); // for db case-insensitive query
    }
}
