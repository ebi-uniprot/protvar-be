package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Dbsnp {
    private String chr;
    private Integer pos;
    private String id;
    private String ref;
    private String alt;
}
