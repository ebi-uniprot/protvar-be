package uk.ac.ebi.uniprot.variation.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Getter
@Setter
public class DSVAssociation {
    private String name;
    private String description;
    private List<DSVDbReferenceObject> dbReferences;
    private List<Evidence> evidences;
    private boolean disease;
}

