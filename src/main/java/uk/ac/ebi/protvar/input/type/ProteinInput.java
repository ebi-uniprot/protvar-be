package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ProteinInput extends UserInput {
    protected String acc; // UniProt accession
    protected Integer pos;
    protected String ref;
    protected String alt;

    List<GenomicInput> derivedGenomicInputs = new ArrayList<>(); // @Getter annotation will generate getDerivedGenomicInputs()

    public ProteinInput(String inputStr) {
        setType(Type.PROTEIN);
        setFormat(Format.INTERNAL_PROTEIN);
        setInputStr(inputStr);
    }

    public static String normalizeAltAllele(String alt, String ref) {
        if (alt != null && alt.equals("=")) {
            alt = ref;
        }
        return alt;
    }
}
