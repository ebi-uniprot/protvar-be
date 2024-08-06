package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Cosmic {
    String id;
    String legacyId;
    String chr;
    Integer pos;
    String ref;
    String alt;
}
