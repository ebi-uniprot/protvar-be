package uk.ac.ebi.protvar.input;

import lombok.Data;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class GenomicVariant {
    String chromosome;
    Integer position;
    String refBase;
    String altBase;

    // Mappings
    List<Gene> genes = new ArrayList<>();
    // todo move caddScore/alleleFreq here?

    public GenomicVariant(String chromosome, Integer position, String refBase) {
        this.chromosome = chromosome;
        this.position = position;
        this.refBase = refBase;
    }
    public GenomicVariant(String chromosome, Integer position, String refBase, String altBase) {
        this(chromosome, position, refBase);
        this.altBase = altBase;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenomicVariant)) return false;
        GenomicVariant that = (GenomicVariant) o;
        return Objects.equals(chromosome, that.chromosome) &&
                Objects.equals(position, that.position) &&
                Objects.equals(refBase, that.refBase) &&
                Objects.equals(altBase, that.altBase);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chromosome, position, refBase, altBase);
    }

    public String getVariantKey() {
        return VariantKey.genomic(chromosome, position);
    }
}
