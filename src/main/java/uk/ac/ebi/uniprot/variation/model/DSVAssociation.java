package uk.ac.ebi.uniprot.variation.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Getter
@Setter
public class DSVAssociation implements Serializable {
    private String name;
    private String description;
    private List<DSVDbReferenceObject> dbReferences;
    private List<Evidence> evidences;
    private boolean disease;
}

