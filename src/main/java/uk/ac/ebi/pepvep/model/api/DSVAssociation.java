package uk.ac.ebi.pepvep.model.api;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DSVAssociation {
    private String name;
    private String description;
    private List<DSVDbReferenceObject> dbReferences;
    private List<Evidence> evidences;
    private boolean disease;
}

