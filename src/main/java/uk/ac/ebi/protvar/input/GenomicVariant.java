package uk.ac.ebi.protvar.input;

import lombok.Data;
import uk.ac.ebi.protvar.model.response.Gene;
import uk.ac.ebi.protvar.utils.VariantKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class GenomicVariant {
    String chr;
    Integer pos;
    String ref;
    String alt;

    // Mappings
    List<Gene> genes = new ArrayList<>();
    // todo move caddScore/alleleFreq here?

    public GenomicVariant(String chr, Integer pos, String ref) {
        this.chr = chr;
        this.pos = pos;
        this.ref = ref;
    }
    public GenomicVariant(String chr, Integer pos, String ref, String alt) {
        this(chr, pos, ref);
        this.alt = alt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenomicVariant)) return false;
        GenomicVariant that = (GenomicVariant) o;
        return Objects.equals(chr, that.chr) &&
                Objects.equals(pos, that.pos) &&
                Objects.equals(ref, that.ref) &&
                Objects.equals(alt, that.alt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(chr, pos, ref, alt);
    }

    public String getVariantKey() {
        return VariantKey.genomic(this.chr, this.pos);
    }
}
