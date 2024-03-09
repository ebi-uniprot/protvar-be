package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Crossmap {
    String chr;
    Integer grch38Pos;
    String grch38Base;
    Integer grch37Pos;
    String grch37Base;

    public String getGroupByChrAnd37Pos() {
        return this.chr+"-"+this.grch37Pos;
    }
}
