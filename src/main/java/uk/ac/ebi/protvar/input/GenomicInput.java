package uk.ac.ebi.protvar.input;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;

@Getter
@Setter
public class GenomicInput extends VariantInput {
    // Parsed fields
    String chromosome;
    Integer position;
    String id; // VCF ID field
    String refBase;
    String altBase;

    // HGVSg input
    String refseqId;
    Boolean refseq37;

    // Derived fields
    Boolean isLiftedFrom37;

    public GenomicInput(String inputStr) {
        super(VariantFormat.INTERNAL_GENOMIC, inputStr);
        // defaults to internal format until explicitly set
    }
    public GenomicInput(String inputStr, String chromosome, Integer position, String refBase) {
        super(VariantFormat.INTERNAL_GENOMIC, inputStr);
        setChromosome(chromosome);
        setPosition(position);
        setRefBase(refBase);
        //derivedGenomicVariants.add(new GenomicVariant(chr, pos, ref));
    }
    public GenomicInput(String inputStr, String chromosome, Integer position, String refBase, String altBase) {
        super(VariantFormat.INTERNAL_GENOMIC, inputStr);
        setChromosome(chromosome);
        setPosition(position);
        setRefBase(refBase);
        setAltBase(altBase);
        //derivedGenomicVariants.add(new GenomicVariant(chr, pos, ref, alt));
    }

    public static VariantInput invalid(String inputStr){
        GenomicInput invalid = new GenomicInput(inputStr);
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

    public GenomicVariant toGenomicVariant() {
        return new GenomicVariant(chromosome, position, refBase, altBase);
    }
}
