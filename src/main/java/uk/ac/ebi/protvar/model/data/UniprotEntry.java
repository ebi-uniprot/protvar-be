package uk.ac.ebi.protvar.model.data;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
@Getter
@Setter
public class UniprotEntry {
    @Id
    private String accession;
}

