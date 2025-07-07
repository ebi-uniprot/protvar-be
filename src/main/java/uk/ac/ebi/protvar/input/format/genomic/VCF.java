package uk.ac.ebi.protvar.input.format.genomic;

import lombok.Getter;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.type.GenomicInput;

@Getter
public class VCF extends GenomicInput {
    public VCF(String inputStr) {
        super(inputStr);
        setFormat(Format.VCF);
    }
}
