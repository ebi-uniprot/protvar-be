package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.ebi.protvar.utils.Commons;
import uk.ac.ebi.protvar.utils.VariantKey;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Crossmap {
    String chr;
    Integer grch38Pos;
    String grch38Base;
    Integer grch37Pos;
    String grch37Base;

    public String getGroupByChrAnd37Pos() {
        return VariantKey.genomic(chr, grch37Pos);
    }
}