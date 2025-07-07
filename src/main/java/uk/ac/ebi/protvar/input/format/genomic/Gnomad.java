package uk.ac.ebi.protvar.input.format.genomic;

import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;

public class Gnomad extends GenomicInput {
    public Gnomad(String inputStr) {
        super(inputStr);
        setFormat(Format.GNOMAD);
    }
}
