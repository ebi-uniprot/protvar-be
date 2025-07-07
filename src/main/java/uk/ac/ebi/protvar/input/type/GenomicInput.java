package uk.ac.ebi.protvar.input.type;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.protvar.input.ErrorConstants;
import uk.ac.ebi.protvar.input.Format;
import uk.ac.ebi.protvar.input.Type;
import uk.ac.ebi.protvar.input.UserInput;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class GenomicInput extends UserInput {
    String chr;
    Integer pos;
    String ref;
    String alt;
    String id;
    Boolean converted;

    List<Gene> genes = new ArrayList<>();
    // TODO move caddScore & alleleFreq here!

    public GenomicInput(String userInput) {
        setType(Type.GENOMIC);
        setFormat(Format.INTERNAL_GENOMIC); // is internal format unless specialised by extending class.
        // Note GenomicInput not abstract so it may be instantiated for e.g. in the case
        // of internal genomic input.
        setInputStr(userInput);
    }
    public GenomicInput(String inputStr, String chr, Integer pos, String ref) {
        setType(Type.GENOMIC);
        setFormat(Format.INTERNAL_GENOMIC);
        setInputStr(inputStr);
        setChr(chr);
        setPos(pos);
        setRef(ref);
    }
    public GenomicInput(String inputStr, String chr, Integer pos, String ref, String alt) {
        this(inputStr, chr, pos, ref);
        setAlt(alt);
    }

    public static UserInput invalid(String userInput){
        GenomicInput invalid = new GenomicInput(userInput);
        invalid.addError(ErrorConstants.INVALID_GENERIC_INPUT);
        return invalid;
    }

    public String getVariantKey() {
        return VariantKey.genomic(this.chr, this.pos);
    }

    public List<GenomicInput> getDerivedGenomicInputs() {
        return List.of(this);
    }

    public static final Map<String, Set<String>> ALTERNATE_ALLELES_MAP = Map.of(
            "A", Set.of("T", "C", "G"),
            "T", Set.of("A", "C", "G"),
            "C", Set.of("A", "T", "G"),
            "G", Set.of("A", "T", "C")
    );

    public static Set<String> getAlternateBases(String refBase) {
        return ALTERNATE_ALLELES_MAP.getOrDefault(refBase.toUpperCase(), Set.of());
    }

    // Overriding equals() to compare two Genomic objects
    @Override
    public boolean equals(Object o) {

        // If the object is compared with itself then return true
        if (o == this) {
            return true;
        }

        /* Check if o is an instance of Genomic or not
          "null instanceof [type]" also returns false */
        if (!(o instanceof GenomicInput)) {
            return false;
        }

        // typecast o to Genomic so that we can compare data members
        GenomicInput g = (GenomicInput) o;

        return this.chr.equals(g.chr)
                && this.pos == g.pos
                && this.ref.equals(g.ref)
                && this.alt.equals(g.alt)
                && this.id.equals(g.id)
                && this.getInputStr().equals(g.getInputStr());
    }
}
