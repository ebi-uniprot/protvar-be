package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Getter
@Setter
public class Dbsnp {
    private String chr;
    private Integer pos;
    @Id
    private String id;
    private String ref;
    private String alt;
}
