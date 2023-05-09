package uk.ac.ebi.uniprot.proteins.model;

import java.util.List;

import lombok.Getter;
import uk.ac.ebi.uniprot.common.model.Evidence;

@Getter
public class Description {
	private String value;
	private List<Evidence> evidences;
}
