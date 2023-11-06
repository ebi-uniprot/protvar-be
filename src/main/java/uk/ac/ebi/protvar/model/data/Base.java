package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Base {
    String chr;
    Integer pos;
    String ref;
    String alt;
}
