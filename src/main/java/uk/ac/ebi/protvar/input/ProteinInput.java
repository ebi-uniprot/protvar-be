package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProteinInput extends UserInput {
    protected String acc; // UniProt accession
    protected Integer pos;
    protected String ref;
    protected String alt;

    public ProteinInput(String inputStr) {
        super(Format.INTERNAL_PROTEIN, inputStr);
    }
}
