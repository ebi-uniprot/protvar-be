package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ClinVar {
    String rcv;
    String vcv;
    String chr;
    Integer pos;
    String ref;
    String alt;
}
