package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
public class Base {
    String chr;
    Integer pos;
    String ref;
    String alt;
}
