package uk.ac.ebi.uniprot.proteins.model;

import java.util.List;

import lombok.Getter;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Getter
public class Reaction {
	private String name;
	private List<DBReference> dbReferences;
	private List<Evidence> evidences;
}
