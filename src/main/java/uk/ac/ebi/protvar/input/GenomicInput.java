package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class GenomicInput extends UserInput {
    // Parsed genomic fields
    String chr;
    Integer pos;
    String ref;
    String alt;
    String id; // VCF ID field
    Boolean converted; // true if coords converted from 37->38

    public GenomicInput(String inputStr) {
        super(Format.INTERNAL_GENOMIC, inputStr);
        // defaults to internal format until explicit set
    }
    public GenomicInput(String inputStr, String chr, Integer pos, String ref) {
        super(Format.INTERNAL_GENOMIC, inputStr);
        setChr(chr);
        setPos(pos);
        setRef(ref);
        //derivedGenomicVariants.add(new GenomicVariant(chr, pos, ref));
    }
    public GenomicInput(String inputStr, String chr, Integer pos, String ref, String alt) {
        super(Format.INTERNAL_GENOMIC, inputStr);
        setChr(chr);
        setPos(pos);
        setRef(ref);
        setAlt(alt);
        //derivedGenomicVariants.add(new GenomicVariant(chr, pos, ref, alt));
    }

    public static UserInput invalid(String userInput){
        GenomicInput invalid = new GenomicInput(userInput);
        invalid.addError(ErrorConstants.INVALID_GENERIC_INPUT);
        return invalid;
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

    public GenomicVariant toGenomicVariant() {
        return new GenomicVariant(chr, pos, ref, alt);
    }
}
