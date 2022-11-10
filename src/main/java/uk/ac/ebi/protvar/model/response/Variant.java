package uk.ac.ebi.protvar.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Variant {

    private String chr;
    private Long pos;
    private String id;
    private String ref;
    private String alt;
}
