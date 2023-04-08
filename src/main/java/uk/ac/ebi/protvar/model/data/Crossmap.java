package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Crossmap {
    String chr;
    Long grch38Pos;
    String grch38Base;
    Long grch37Pos;
    String grch37Base;

    public String getGroupByChrAnd37Pos() {
        return this.chr+"-"+this.grch37Pos;
    }
}
