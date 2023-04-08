package uk.ac.ebi.protvar.model.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Dbsnp {

    private String chr;
    private Long pos;
    private String id;
    private String ref;
    private String alt;
}
