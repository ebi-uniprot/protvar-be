package uk.ac.ebi.uniprot.proteins.model;

import java.io.Serializable;
import java.util.List;

import lombok.Getter;
import uk.ac.ebi.uniprot.domain.features.Evidence;

@Getter
public class Reaction implements Serializable {
	private String name;
	private List<DBReference> dbReferences;
	private List<Evidence> evidences;
}
