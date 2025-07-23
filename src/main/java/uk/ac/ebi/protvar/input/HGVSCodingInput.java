package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HGVSCodingInput extends VariantInput {
    // Parsed fields
    String refseqId;       // e.g. "NM_020975.6"
    String geneSymbol;     // optional, e.g. "RET"

    Integer position;      // e.g. 3105
    String refBase;        // e.g. "G"
    String altBase;        // e.g. "A"

    // optional protein effect  e.g. "p.Glu1035Glu"
    String refAA;          // e.g. "Glu"
    Integer aaPos;         // e.g. 1035
    String altAA;          // e.g. "Glu"

    // Derived fields
    String derivedUniprotAcc;
    Integer derivedProtPos;
    Integer derivedCodonPos;

    public HGVSCodingInput(String inputStr) {
        super(VariantFormat.HGVS_CODING, inputStr);
    }
}
