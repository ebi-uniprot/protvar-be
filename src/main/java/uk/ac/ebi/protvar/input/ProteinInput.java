package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProteinInput extends VariantInput {
    // Parsed fields
    String accession;
    Integer position;
    String refAA;
    String altAA;
    // True when the variant AA was explicitly given as "?" (unknown consequence, e.g. p.Met1?).
    // altAA stays null (position- but not variant-specific); this distinguishes "unknown" from
    // "omitted" so a specific message can be shown.
    boolean altUnknown;

    // HGVSp input
    String refseqId;

    public ProteinInput(String inputStr) {
        super(VariantFormat.INTERNAL_PROTEIN, inputStr);
    }
}
